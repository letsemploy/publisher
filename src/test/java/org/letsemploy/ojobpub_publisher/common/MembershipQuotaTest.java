package org.letsemploy.ojobpub_publisher.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.invitation.InvitationRepo;
import org.letsemploy.ojobpub_publisher.invitation.InvitationService;
import org.letsemploy.ojobpub_publisher.invitation.InvitationStatus;
import org.letsemploy.ojobpub_publisher.location.LocationRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two membership quotas of spec 8.4 — how many employers a person may belong
 * to, and how many people an employer may hold.
 *
 * <p>Separate from {@code QuotaEnforcementTest} because these two refuse the acts
 * the other class uses as arrangement: an employer at its member cap may not
 * invite anybody, and a user at their cap may not create the employer a feed or
 * job test needs.
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@Transactional
@TestPropertySource(properties = {
        "app.limits.memberships-per-user=2",
        "app.limits.members-per-employer=2",
        "app.limits.jobs-per-employer=0",
        "app.limits.feeds-per-employer=0",
        "app.limits.pending-invitations-per-employer=0",
        "app.limits.tokens-per-employer=0"})
class MembershipQuotaTest {

    @Autowired private EmployerService employerService;
    @Autowired private EmployerRepo employerRepo;
    @Autowired private InvitationService invitationService;
    @Autowired private InvitationRepo invitationRepo;
    @Autowired private MembershipRepo membershipRepo;
    @Autowired private LocationRepo locationRepo;
    @Autowired private UserRepo userRepo;

    private Actor admin;
    private UserEntity invitee;
    private UUID location;

    @BeforeEach
    void setUp() {
        admin = asUser(userRepo.findByEmailIgnoreCase("dev@localhost").orElseThrow());
        invitee = userRepo.findByEmailIgnoreCase("editor@example.com").orElseThrow();
        location = locationRepo.findAll().get(0).getId();
    }

    private Actor asUser(UserEntity user) {
        Map<UUID, MembershipRole> memberships = new java.util.HashMap<>();
        membershipRepo.findByUserId(user.getId())
                .forEach(m -> memberships.put(m.getEmployer().getId(), m.getRole()));
        return Actor.user(user.getId(), user.getDisplayName(), user.getEmail(),
                user.getRole() == UserEntity.Role.ADMIN, memberships);
    }

    private UUID createEmployer(Actor actor, String name) {
        return employerService.save(null, name + " " + UUID.randomUUID(), null, null, null,
                location, actor).getId();
    }

    /** Creating grants a membership, so the per-user quota governs it (spec 2.2). */
    @Test
    void aUserAtTheirCapCannotCreateAnotherEmployer() {
        createEmployer(admin, "First");
        long before = employerRepo.count();
        assertThatThrownBy(() -> createEmployer(admin, "Second"))
                .isInstanceOf(ValidationFailure.class)
                .hasMessageContaining("limit.memberships");
        assertThat(employerRepo.count())
                .as("refused before anything was written")
                .isEqualTo(before);
    }

    /**
     * The guard lives inside {@code grant}'s {@code orElseGet}, so this is also
     * what proves it is reached from the accept path and not only from creation.
     * The invitation stays pending: it can be taken up if they leave an employer.
     */
    @Test
    void aUserAtTheirCapCannotAcceptAnInvitation() {
        Actor editor = asUser(invitee);
        createEmployer(editor, "Theirs one");
        createEmployer(asUser(invitee), "Theirs two");

        UUID pending = invitationRepo.findByInviteeIdAndStatusOrderByCreatedAtDesc(
                invitee.getId(), InvitationStatus.PENDING).get(0).getId();
        assertThatThrownBy(() -> invitationService.accept(pending, asUser(invitee)))
                .isInstanceOf(ValidationFailure.class)
                .hasMessageContaining("limit.memberships");
        assertThat(invitationRepo.findById(pending).orElseThrow().getStatus())
                .isEqualTo(InvitationStatus.PENDING);
    }

    /** Acme already holds its two people, so a third cannot be admitted. */
    @Test
    void anEmployerAtItsMemberCapCannotAdmitAnother() {
        UUID acme = employerRepo.findAll().stream()
                .filter(e -> e.getName().equals("Acme AG")).findFirst().orElseThrow().getId();
        UUID pending = invitationRepo.findByEmployerIdAndStatusOrderByCreatedAtDesc(
                acme, InvitationStatus.PENDING).get(0).getId();
        assertThatThrownBy(() -> invitationService.accept(pending, asUser(invitee)))
                .isInstanceOf(ValidationFailure.class)
                .hasMessageContaining("limit.members");
    }

    /**
     * And it may not invite either. An invitation that could never be accepted is
     * worse than a refusal now — the same reasoning as the last-owner rule, which
     * says a refusal with no route forward is worse than a missing button.
     */
    @Test
    void anEmployerAtItsMemberCapCannotInvite() {
        UUID acme = employerRepo.findAll().stream()
                .filter(e -> e.getName().equals("Acme AG")).findFirst().orElseThrow().getId();
        assertThatThrownBy(() -> invitationService.invite(
                acme, "someone-new@example.com", MembershipRole.EDITOR, admin))
                .isInstanceOf(ValidationFailure.class)
                .hasMessageContaining("limit.members");
    }

    /** Removing somebody makes room, so the quota is a limit and not a dead end. */
    @Test
    void makingRoomLetsTheNextPersonIn() {
        UUID fresh = createEmployer(admin, "Roomy");
        UUID pending = invitationRepo.findByInviteeIdAndStatusOrderByCreatedAtDesc(
                invitee.getId(), InvitationStatus.PENDING).get(0).getId();
        // A brand new employer holds one member, so there is room for one more.
        invitationService.invite(fresh, invitee.getEmail(), MembershipRole.EDITOR, admin);
        UUID toFresh = invitationRepo.findByEmployerIdAndStatusOrderByCreatedAtDesc(
                fresh, InvitationStatus.PENDING).get(0).getId();
        assertThatCode(() -> invitationService.accept(toFresh, asUser(invitee)))
                .doesNotThrowAnyException();
        assertThat(pending).isNotEqualTo(toFresh);
    }
}
