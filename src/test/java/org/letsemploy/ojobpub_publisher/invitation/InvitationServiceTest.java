package org.letsemploy.ojobpub_publisher.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.AppUser;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** The rules of specification 2.6, exercised against the database. */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@Transactional
class InvitationServiceTest {

    @Autowired
    private InvitationService invitationService;
    @Autowired
    private MembershipService membershipService;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private EmployerRepo employerRepo;
    @Autowired
    private InvitationRepo invitationRepo;
    @Autowired
    private MembershipRepo membershipRepo;

    private Employer employer;
    private AppUser admin;
    private UserEntity invitee;
    private UserEntity member;

    @BeforeEach
    void setUp() {
        employer = employerRepo.findAll().get(0);
        admin = asUser(userRepo.findByEmailIgnoreCase("dev@localhost").orElseThrow());
        invitee = userRepo.findByEmailIgnoreCase("editor@example.com").orElseThrow();
        member = userRepo.findByEmailIgnoreCase("member@example.com").orElseThrow();
    }

    private AppUser asUser(UserEntity user) {
        Map<UUID, MembershipRole> memberships = new java.util.HashMap<>();
        membershipRepo.findByUserId(user.getId())
                .forEach(m -> memberships.put(m.getEmployer().getId(), m.getRole()));
        return new AppUser(user.getId(), user.getDisplayName(), user.getEmail(),
                user.getRole() == UserEntity.Role.ADMIN, memberships);
    }

    private Invitation pendingForInvitee() {
        return invitationRepo.findByInviteeIdAndStatusOrderByCreatedAtDesc(
                invitee.getId(), InvitationStatus.PENDING).get(0);
    }

    // ------------------------------------------------------- non-disclosure

    /**
     * An unregistered address is indistinguishable from success: the form must not
     * reveal which addresses have accounts (spec 2.6).
     */
    @Test
    void unknownEmailIsIndistinguishableFromSuccess() {
        long before = invitationRepo.count();
        assertThat(invitationService.invite(employer.getId(), "nobody@example.com",
                MembershipRole.EDITOR, admin)).isEqualTo(InvitationService.InviteOutcome.SENT);
        assertThat(invitationRepo.count()).as("no invitation row created").isEqualTo(before);
    }

    @Test
    void existingMemberIsReported() {
        assertThat(invitationService.invite(employer.getId(), "member@example.com",
                MembershipRole.EDITOR, admin))
                .isEqualTo(InvitationService.InviteOutcome.ALREADY_MEMBER);
    }

    @Test
    void duplicatePendingInvitationIsReported() {
        assertThat(invitationService.invite(employer.getId(), "editor@example.com",
                MembershipRole.EDITOR, admin))
                .isEqualTo(InvitationService.InviteOutcome.ALREADY_INVITED);
    }

    // ------------------------------------------------------------ who invites

    /** An owner of the employer may invite, without being a platform admin (spec 2.6). */
    @Test
    void ownerMayInvite() {
        membershipService.changeRole(employer.getId(), member.getId(), MembershipRole.OWNER, admin);
        AppUser owner = asUser(member);
        assertThat(owner.isAdmin()).as("not platform staff").isFalse();

        assertThat(invitationService.invite(employer.getId(), "fresh@example.com",
                MembershipRole.EDITOR, owner)).isEqualTo(InvitationService.InviteOutcome.SENT);
    }

    /** An editor works on the content, not on who else gets in (spec 2.6). */
    @Test
    void editorMayNotInvite() {
        AppUser editor = asUser(member);
        assertThatThrownBy(() -> invitationService.invite(employer.getId(), "x@example.com",
                MembershipRole.EDITOR, editor)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aStrangerMayNotInvite() {
        AppUser stranger = asUser(invitee);
        assertThatThrownBy(() -> invitationService.invite(employer.getId(), "x@example.com",
                MembershipRole.EDITOR, stranger)).isInstanceOf(NotFoundException.class);
    }

    // --------------------------------------------------------------- accepting

    /** Accepting creates the membership with the role the invitation carried. */
    @Test
    void acceptingGrantsTheRoleTheInvitationCarried() {
        Invitation pending = pendingForInvitee();
        assertThat(pending.getRole()).isEqualTo(MembershipRole.EDITOR);

        invitationService.accept(pending.getId(), asUser(invitee));

        assertThat(membershipRepo.findByUserIdAndEmployerId(invitee.getId(), employer.getId()))
                .get().extracting(m -> m.getRole()).isEqualTo(MembershipRole.EDITOR);
    }

    /** Inviting straight to owner is ordinary: a founder handing over (spec 2.6). */
    @Test
    void invitingStraightToOwnerGrantsOwnership() {
        invitationService.revoke(pendingForInvitee().getId(), admin);
        invitationService.invite(employer.getId(), "editor@example.com", MembershipRole.OWNER, admin);

        invitationService.accept(pendingForInvitee().getId(), asUser(invitee));

        assertThat(membershipRepo.findByUserIdAndEmployerId(invitee.getId(), employer.getId()))
                .get().extracting(m -> m.getRole()).isEqualTo(MembershipRole.OWNER);
    }

    @Test
    void decliningGrantsNothingAndAllowsAFreshInvitation() {
        invitationService.decline(pendingForInvitee().getId(), asUser(invitee));

        assertThat(membershipRepo.findByUserIdAndEmployerId(invitee.getId(), employer.getId()))
                .isEmpty();
        assertThat(invitationService.invite(employer.getId(), "editor@example.com",
                MembershipRole.EDITOR, admin))
                .as("a declined invitation may be sent again")
                .isEqualTo(InvitationService.InviteOutcome.SENT);
    }

    /** Only the invitee may respond; anyone else gets 404, not 403 (spec 7.16). */
    @Test
    void respondingToSomeoneElsesInvitationIsNotFound() {
        Invitation pending = pendingForInvitee();
        assertThatThrownBy(() -> invitationService.accept(pending.getId(), admin))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aResolvedInvitationIsNeverReopened() {
        Invitation pending = pendingForInvitee();
        invitationService.decline(pending.getId(), asUser(invitee));
        assertThatThrownBy(() -> invitationService.accept(pending.getId(), asUser(invitee)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void revokingClearsThePendingInvitation() {
        Invitation pending = pendingForInvitee();
        invitationService.revoke(pending.getId(), admin);

        assertThat(invitationRepo.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(InvitationStatus.REVOKED);
        assertThat(invitationService.pendingFor(asUser(invitee))).isEmpty();
    }

    @Test
    void pendingCountDrivesTheSidebarBadge() {
        assertThat(invitationService.countPendingFor(asUser(invitee))).isEqualTo(1);
        assertThat(invitationService.countPendingFor(admin)).isZero();
    }

    @Test
    void anonymousUserHasNoInvitations() {
        AppUser anonymous = new AppUser(null, "anonymous", null, false, Map.of());
        assertThat(invitationService.pendingFor(anonymous)).isEmpty();
        assertThat(invitationService.countPendingFor(anonymous)).isZero();
    }
}
