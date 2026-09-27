package org.letsemploy.ojobpub_publisher.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.employer.Headquarters;
import org.letsemploy.ojobpub_publisher.invitation.InvitationService;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.letsemploy.ojobpub_publisher.tag.TagService;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.letsemploy.ojobpub_publisher.web.view.ActivityRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The audit log (spec 3.12, 7.21, 7.22): what is recorded, and who may read it.
 *
 * <p>One table, two scopes. Each test checks that an event lands in the log - or
 * logs - it belongs to, and stays out of the ones it does not: an editor never
 * reads the owners-only rows, a stranger reads nothing, and a person's own log is
 * theirs and the admins'.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class AuditLogTest {

    /** Fixed ids from the seed. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID DEV_ADMIN = UUID.fromString("11111111-1111-4111-8111-111111111111");
    /** An editor of Acme. */
    private static final UUID MARA = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AuditService auditService;
    @Autowired
    private AuditRepo auditRepo;
    @Autowired
    private Views views;
    @Autowired
    private TagService tagService;
    @Autowired
    private MembershipService membershipService;
    @Autowired
    private InvitationService invitationService;
    @Autowired
    private EmployerService employerService;
    @Autowired
    private EmployerRepo employerRepo;
    @Autowired
    private UserRepo userRepo;

    private Employer acme;
    private final Actor admin = Actor.user(DEV_ADMIN, "dev@localhost", "dev@localhost", true,
            Map.of(ACME, MembershipRole.OWNER));
    private final Actor mara = Actor.user(MARA, "Mara Member", "member@example.com", false,
            Map.of(ACME, MembershipRole.EDITOR));
    private final Actor stranger = Actor.user(UUID.randomUUID(), "Stan Stranger", null, false, Map.of());

    @BeforeEach
    void acme() {
        acme = employerRepo.findById(ACME).orElseThrow();
    }

    private List<AuditEvent> acmeLogAs(Actor reader) {
        return auditService.forEmployers(List.of(ACME), reader, 0).getContent();
    }

    private static List<AuditAction> actions(List<AuditEvent> events) {
        return events.stream().map(AuditEvent::getAction).toList();
    }

    // ------------------------------------------------------ the employer's log

    /** A change to the employer's content: in its log, for every member. */
    @Test
    void everyMemberReadsTheEmployersChanges() {
        tagService.create(acme, "audited", admin);

        AuditEvent event = acmeLogAs(mara).get(0);
        assertThat(event.getAction()).isEqualTo(AuditAction.TAG_CREATED);
        assertThat(event.getTargetLabel()).isEqualTo("audited");
        assertThat(event.getActorLabel()).isEqualTo("dev@localhost");
        assertThat(event.getActorType()).isEqualTo(AuditEvent.ActorType.USER);
        assertThat(event.getEmployerLabel()).isEqualTo("Acme AG");
        assertThat(event.getSubjectUserId()).as("about nobody in particular").isNull();
    }

    /**
     * Invitations and tokens are the owners' business on every other screen
     * (spec 7.17, 7.18), so an editor's view of the log leaves them out too.
     */
    @Test
    void anEditorDoesNotReadTheOwnersOnlyRows() {
        invitationService.invite(ACME, "nobody-yet@example.com", MembershipRole.EDITOR, admin);

        assertThat(actions(acmeLogAs(admin))).contains(AuditAction.INVITATION_SENT);
        assertThat(actions(acmeLogAs(mara))).doesNotContain(AuditAction.INVITATION_SENT);
    }

    /** Leaving an employer out of the scope is not a refusal - it is simply not there. */
    @Test
    void aStrangerReadsNothingOfTheEmployer() {
        tagService.create(acme, "audited", admin);
        assertThat(acmeLogAs(stranger)).isEmpty();
    }

    /**
     * The row for an invitation is the same whether or not the address belongs to
     * an account, or the log would answer what the invite form refuses to (spec 2.6).
     */
    @Test
    void anInvitationRowDoesNotRevealWhetherTheAddressHasAnAccount() {
        UserEntity someone = new UserEntity();
        someone.setIssuer("test");
        someone.setSubject("registered-" + UUID.randomUUID());
        someone.setEmail("registered-" + UUID.randomUUID() + "@example.com");
        someone.setDisplayName("Rita Registered");
        someone = userRepo.save(someone);

        invitationService.invite(ACME, someone.getEmail(), MembershipRole.EDITOR, admin);
        invitationService.invite(ACME, "unregistered@example.com", MembershipRole.EDITOR, admin);

        // The newest two: tests that are not transactional leave rows behind, and
        // the log - by design - outlives what they cleaned up.
        List<ActivityRow> rows = acmeLogAs(admin).stream()
                .filter(e -> e.getAction() == AuditAction.INVITATION_SENT)
                .limit(2).map(views::activityRow).toList();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isEqualTo(rows.get(1));
        assertThat(rows.get(0).getText()).doesNotContain("Rita").doesNotContain("@");
        // Only the invitee's own log learns it was about them.
        assertThat(actions(auditService.forPerson(someone.getId(),
                Actor.user(someone.getId(), "Rita Registered", someone.getEmail(), false, Map.of()), 0)
                .getContent())).containsExactly(AuditAction.INVITATION_SENT);
    }

    /** A token is named as the token, never as the person who created it (spec 3.11). */
    @Test
    void aTokenIsRecordedAsTheToken() {
        UUID tokenId = UUID.randomUUID();
        tagService.create(acme, "from-ci", Actor.serviceToken(tokenId, "ci (ojp_abc)", ACME,
                MembershipRole.EDITOR, Set.of(TokenScope.JOBS_WRITE)));

        AuditEvent event = acmeLogAs(admin).get(0);
        assertThat(event.getActorType()).isEqualTo(AuditEvent.ActorType.TOKEN);
        assertThat(event.getActorTokenId()).isEqualTo(tokenId);
        assertThat(event.getActorUserId()).isNull();
        assertThat(event.getActorLabel()).isEqualTo("ci (ojp_abc)");
    }

    // --------------------------------------------------------- a person's log

    /** Done to a member: in the employer's log and in the member's own - one row. */
    @Test
    void aSuspensionIsInBothLogs() {
        membershipService.suspend(ACME, MARA, admin);

        AuditEvent inAcme = acmeLogAs(admin).get(0);
        AuditEvent inHers = auditService.forPerson(MARA, mara, 0).getContent().get(0);
        assertThat(inAcme.getAction()).isEqualTo(AuditAction.MEMBER_SUSPENDED);
        assertThat(inHers.getId()).isEqualTo(inAcme.getId());
        assertThat(inHers.getTargetLabel()).isEqualTo("Mara Member");
    }

    /** One's own log is one's own - and platform staff's, and nobody else's. */
    @Test
    void aPersonsLogIsTheirsAndTheAdmins() {
        membershipService.suspend(ACME, MARA, admin);

        assertThat(auditService.forPerson(MARA, admin, 0).getContent()).isNotEmpty();
        assertThatThrownBy(() -> auditService.forPerson(MARA, stranger, 0))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> auditService.everything(mara, 0)).isInstanceOf(NotFoundException.class);
        Actor token = Actor.serviceToken(UUID.randomUUID(), "ci", ACME, MembershipRole.OWNER,
                Set.of(TokenScope.PEOPLE_READ));
        assertThatThrownBy(() -> auditService.forEmployers(List.of(ACME), token, 0))
                .isInstanceOf(NotFoundException.class);
    }

    /** What I did is mine to read, even though it was not done to me. */
    @Test
    void aPersonsLogHoldsWhatTheyDid() {
        tagService.create(acme, "maras-tag", mara);
        assertThat(auditService.forPerson(MARA, mara, 0).getContent())
                .extracting(AuditEvent::getTargetLabel).contains("maras-tag");
    }

    // ------------------------------------------------------ what is not written

    /** A refusal is not something that happened. */
    @Test
    void aRefusalRecordsNothing() {
        long before = auditRepo.count();
        assertThatThrownBy(() -> tagService.create(acme, " ", admin)).isInstanceOf(ValidationFailure.class);
        assertThatThrownBy(() -> membershipService.suspend(ACME, DEV_ADMIN, admin))
                .isInstanceOf(ValidationFailure.class);
        assertThatThrownBy(() -> membershipService.suspend(ACME, MARA, mara))
                .isInstanceOf(NotFoundException.class);
        assertThat(auditRepo.count()).isEqualTo(before);
    }

    /** Nor is a change that changes nothing. */
    @Test
    void aNoOpRecordsNothing() {
        long before = auditRepo.count();
        membershipService.changeRole(ACME, MARA, MembershipRole.EDITOR, admin);
        membershipService.reinstate(ACME, MARA, admin);
        assertThat(auditRepo.count()).isEqualTo(before);
    }

    // ---------------------------------------------------------------- deletion

    /**
     * Deleting an employer takes everything that references it - but not the
     * record of the deletion, which stays readable by admins (spec 3.12).
     */
    @Test
    void theRecordOfADeletionOutlivesTheEmployer() {
        Employer doomed = employerService.save(null, "Short-lived GmbH", null, null, null,
                Headquarters.newLocation("Olten", "CH"), admin);
        Actor owner = Actor.user(DEV_ADMIN, "dev@localhost", null, false,
                Map.of(doomed.getId(), MembershipRole.OWNER));
        employerService.delete(doomed.getId(), "Short-lived GmbH", owner);

        assertThat(employerRepo.findById(doomed.getId())).isEmpty();
        List<AuditEvent> log = auditService.forEmployers(List.of(doomed.getId()), admin, 0).getContent();
        assertThat(actions(log)).containsSubsequence(AuditAction.EMPLOYER_DELETED, AuditAction.EMPLOYER_CREATED);
        assertThat(log.get(0).getEmployerLabel()).isEqualTo("Short-lived GmbH");
        assertThat(actions(auditService.everything(admin, 0).getContent())).contains(AuditAction.EMPLOYER_DELETED);
        // A former member no longer has it in scope.
        assertThat(auditService.forEmployers(List.of(doomed.getId()), stranger, 0).getContent()).isEmpty();
    }

    // ----------------------------------------------------------------- screens

    /** Viewing as someone is in their own log, naming the admin (spec 2.9). */
    @Test
    void theViewedUserSeesThatTheyWereViewed() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/users/" + MARA + "/impersonate").session(session))
                .andExpect(status().is3xxRedirection());
        String hers = mvc.perform(get("/activity/mine").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(hers).contains("started viewing the application as Mara Member").contains("dev@localhost");
        mvc.perform(post("/impersonation/stop").session(session));

        assertThat(actions(auditService.forPerson(MARA, admin, 0).getContent()))
                .containsSubsequence(AuditAction.VIEW_AS_STOPPED, AuditAction.VIEW_AS_STARTED);
    }

    /** The employer's log on Activity; a user's own log, reachable from Users. */
    @Test
    void theScreensRenderTheEvents() throws Exception {
        tagService.create(acme, "on-screen", admin);
        membershipService.suspend(ACME, MARA, admin);

        assertThat(mvc.perform(get("/activity")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .contains("added the tag “on-screen”").contains("suspended Mara Member");
        assertThat(mvc.perform(get("/users/" + MARA + "/activity")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .contains("suspended Mara Member").doesNotContain("on-screen");
        mvc.perform(get("/users/" + UUID.randomUUID() + "/activity")).andExpect(status().isNotFound());
    }

    /**
     * An event about a person belongs to no employer: the Activity screen, which
     * covers employers, never shows it, and platform staff read it under "All
     * activity" - a route of its own, because a single-employer installation never
     * offers "All employers" (spec 2.5).
     */
    @Test
    void eventsAboutPeopleAreOnlyInAllActivity() throws Exception {
        membershipService.suspend(ACME, MARA, admin);
        UserEntity maraUser = userRepo.findById(MARA).orElseThrow();
        auditRepo.save(AuditEvent.of(AuditAction.ADMIN_GRANTED, null).about(maraUser)
                .target(MARA, "Mara Member"));

        assertThat(mvc.perform(get("/activity")).andReturn().getResponse().getContentAsString())
                .contains("suspended Mara Member").doesNotContain("made Mara Member a platform admin")
                .contains("href=\"/activity/all\"");
        assertThat(mvc.perform(get("/activity/all")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .contains("suspended Mara Member").contains("made Mara Member a platform admin")
                .contains("The application");
    }
}
