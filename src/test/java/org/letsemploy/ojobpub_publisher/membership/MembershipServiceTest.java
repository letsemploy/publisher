package org.letsemploy.ojobpub_publisher.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.location.LocationRepo;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Ownership and role changes (spec 2.7). */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@Transactional
class MembershipServiceTest {

    @Autowired
    private MembershipService membershipService;
    @Autowired
    private MembershipRepo membershipRepo;
    @Autowired
    private EmployerService employerService;
    @Autowired
    private EmployerRepo employerRepo;
    @Autowired
    private LocationRepo locationRepo;
    @Autowired
    private UserRepo userRepo;

    private Employer employer;
    private Actor admin;
    private UserEntity owner;
    private UserEntity editor;
    private UserEntity outsider;

    @BeforeEach
    void setUp() {
        employer = employerRepo.findAll().get(0);
        owner = userRepo.findByEmailIgnoreCase("dev@localhost").orElseThrow();
        editor = userRepo.findByEmailIgnoreCase("member@example.com").orElseThrow();
        outsider = userRepo.findByEmailIgnoreCase("editor@example.com").orElseThrow();
        admin = asUser(owner);
    }

    private Actor asUser(UserEntity user) {
        Map<UUID, MembershipRole> memberships = new java.util.HashMap<>();
        membershipRepo.findByUserId(user.getId())
                .forEach(m -> memberships.put(m.getEmployer().getId(), m.getRole()));
        return Actor.user(user.getId(), user.getDisplayName(), user.getEmail(),
                user.getRole() == UserEntity.Role.ADMIN, memberships);
    }

    private MembershipRole roleOf(UserEntity user) {
        return membershipRepo.findByUserIdAndEmployerId(user.getId(), employer.getId())
                .orElseThrow().getRole();
    }

    // ------------------------------------------------- creating an employer

    /** The creator becomes the first owner; no employer exists unowned (spec 2.7). */
    @Test
    void creatingAnEmployerMakesTheCreatorItsOwner() {
        Actor creator = asUser(outsider);
        assertThat(creator.isAdmin()).as("an ordinary user").isFalse();

        Employer created = employerService.save(null, "Newco AG", null, null, null,
                locationRepo.findAll().get(0).getId(), creator);

        assertThat(membershipRepo.findByUserIdAndEmployerId(outsider.getId(), created.getId()))
                .get().extracting(Membership::getRole).isEqualTo(MembershipRole.OWNER);
        assertThat(membershipRepo.countByEmployerIdAndRoleAndUserIsNotNull(created.getId(), MembershipRole.OWNER))
                .isEqualTo(1);
    }

    /** Editing the employer record takes an owner membership or admin (spec 2.1). */
    @Test
    void anEditorMayNotEditTheEmployerRecord() {
        Actor asEditor = asUser(editor);
        assertThatThrownBy(() -> employerService.save(employer.getId(), "Renamed", null, null, null,
                employer.getHeadquarters().getId(), asEditor))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void anOwnerMayEditTheEmployerRecord() {
        membershipService.changeRole(employer.getId(), editor.getId(), MembershipRole.OWNER, admin);
        employerService.save(employer.getId(), "Renamed AG", null, null, null,
                employer.getHeadquarters().getId(), asUser(editor));
        assertThat(employerRepo.findById(employer.getId()).orElseThrow().getName())
                .isEqualTo("Renamed AG");
    }

    // ------------------------------------------------------- changing roles

    @Test
    void anOwnerMayPromoteAndDemoteOthers() {
        membershipService.changeRole(employer.getId(), editor.getId(), MembershipRole.OWNER, admin);
        assertThat(roleOf(editor)).isEqualTo(MembershipRole.OWNER);

        membershipService.changeRole(employer.getId(), editor.getId(), MembershipRole.EDITOR, admin);
        assertThat(roleOf(editor)).isEqualTo(MembershipRole.EDITOR);
    }

    /** Owners are equal: a promoted owner may demote the one who promoted them. */
    @Test
    void ownersAreEqual() {
        membershipService.changeRole(employer.getId(), editor.getId(), MembershipRole.OWNER, admin);
        membershipService.changeRole(employer.getId(), owner.getId(), MembershipRole.EDITOR,
                asUser(editor));
        assertThat(roleOf(owner)).isEqualTo(MembershipRole.EDITOR);
    }

    @Test
    void anEditorMayNotChangeRoles() {
        Actor asEditor = asUser(editor);
        assertThatThrownBy(() -> membershipService.changeRole(employer.getId(), owner.getId(),
                MembershipRole.EDITOR, asEditor)).isInstanceOf(NotFoundException.class);
    }

    // --------------------------------------------------------- the last owner

    /** An employer must always have at least one owner (spec 2.7). */
    @Test
    void theLastOwnerCannotBeDemoted() {
        assertThatThrownBy(() -> membershipService.changeRole(employer.getId(), owner.getId(),
                MembershipRole.EDITOR, admin))
                .isInstanceOf(ValidationFailure.class)
                .hasMessageContaining("role");
        assertThat(roleOf(owner)).isEqualTo(MembershipRole.OWNER);
    }

    @Test
    void theLastOwnerCannotBeRemoved() {
        assertThatThrownBy(() -> membershipService.remove(employer.getId(), owner.getId(), admin))
                .isInstanceOf(ValidationFailure.class);
        assertThat(membershipRepo.findByUserIdAndEmployerId(owner.getId(), employer.getId()))
                .isPresent();
    }

    /** The refusal is not a dead end: promote someone else and it lifts. */
    @Test
    void promotingSomeoneElseReleasesTheLastOwner() {
        membershipService.changeRole(employer.getId(), editor.getId(), MembershipRole.OWNER, admin);

        membershipService.changeRole(employer.getId(), owner.getId(), MembershipRole.EDITOR, admin);

        assertThat(roleOf(owner)).isEqualTo(MembershipRole.EDITOR);
        assertThat(membershipRepo.countByEmployerIdAndRoleAndUserIsNotNull(employer.getId(), MembershipRole.OWNER))
                .isEqualTo(1);
    }

    @Test
    void isLastOwnerFlagsOnlyTheLastOne() {
        Membership ownerMembership = membershipRepo
                .findByUserIdAndEmployerId(owner.getId(), employer.getId()).orElseThrow();
        Membership editorMembership = membershipRepo
                .findByUserIdAndEmployerId(editor.getId(), employer.getId()).orElseThrow();

        assertThat(membershipService.isLastOwner(employer.getId(), ownerMembership)).isTrue();
        assertThat(membershipService.isLastOwner(employer.getId(), editorMembership)).isFalse();
    }

    // ------------------------------------------------------------- removing

    @Test
    void anOwnerMayRemoveAnEditor() {
        membershipService.remove(employer.getId(), editor.getId(), admin);
        assertThat(membershipRepo.findByUserIdAndEmployerId(editor.getId(), employer.getId()))
                .isEmpty();
    }

    @Test
    void canAdministerIsOwnershipOrPlatformAdmin() {
        assertThat(membershipService.canAdminister(admin, employer.getId())).isTrue();
        assertThat(membershipService.canAdminister(asUser(editor), employer.getId())).isFalse();
        assertThat(membershipService.canAdminister(asUser(outsider), employer.getId())).isFalse();
    }
}
