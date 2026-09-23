package org.letsemploy.ojobpub_publisher.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.feed.FeedRepo;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.invitation.InvitationRepo;
import org.letsemploy.ojobpub_publisher.invitation.InvitationService;
import org.letsemploy.ojobpub_publisher.invitation.InvitationStatus;
import org.letsemploy.ojobpub_publisher.job.JobForm;
import org.letsemploy.ojobpub_publisher.job.JobRepo;
import org.letsemploy.ojobpub_publisher.job.JobService;
import org.letsemploy.ojobpub_publisher.location.LocationRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.letsemploy.ojobpub_publisher.token.ServiceToken;
import org.letsemploy.ojobpub_publisher.token.ServiceTokenRepo;
import org.letsemploy.ojobpub_publisher.token.ServiceTokenService;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * The six quotas of spec 8.4, against the real seed data.
 *
 * <p>Every cap is set to exactly what {@code data.sql} already contains, so each
 * test is "create one more, be refused" and each has a companion proving the
 * refusal lifts once the cause is removed - the convention
 * {@code promotingSomeoneElseReleasesTheLastOwner} set for the last-owner rule.
 * A quota that can only refuse is a trap; one that can be escaped is a limit.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
@TestPropertySource(properties = {
        "app.limits.jobs-per-employer=6",
        "app.limits.feeds-per-employer=2",
        "app.limits.pending-invitations-per-employer=1",
        "app.limits.tokens-per-employer=1",
        // The two membership quotas are off here and switched on in
        // MembershipQuotaTest. They interfere with these: an employer at its
        // member cap cannot invite anybody, which would refuse the invitation
        // tests below for the wrong reason.
        "app.limits.members-per-employer=0",
        "app.limits.memberships-per-user=0"})
class QuotaEnforcementTest {

    @Autowired private EmployerService employerService;
    @Autowired private EmployerRepo employerRepo;
    @Autowired private JobService jobService;
    @Autowired private JobRepo jobRepo;
    @Autowired private FeedService feedService;
    @Autowired private FeedRepo feedRepo;
    @Autowired private InvitationService invitationService;
    @Autowired private InvitationRepo invitationRepo;
    @Autowired private ServiceTokenService tokenService;
    @Autowired private ServiceTokenRepo tokenRepo;
    @Autowired private MembershipRepo membershipRepo;
    @Autowired private LocationRepo locationRepo;
    @Autowired private UserRepo userRepo;

    private Employer acme;
    private Actor admin;

    @BeforeEach
    void setUp() {
        acme = employerRepo.findAll().get(0);
        UserEntity dev = userRepo.findByEmailIgnoreCase("dev@localhost").orElseThrow();
        admin = asUser(dev);
    }

    private Actor asUser(UserEntity user) {
        Map<UUID, MembershipRole> memberships = new java.util.HashMap<>();
        membershipRepo.findByUserId(user.getId())
                .forEach(m -> memberships.put(m.getEmployer().getId(), m.getRole()));
        return Actor.user(user.getId(), user.getDisplayName(), user.getEmail(),
                user.getRole() == UserEntity.Role.ADMIN, memberships);
    }

    private JobForm jobForm(String title) {
        JobForm form = new JobForm();
        form.setTitle(title);
        form.setUrl("https://www.acme.example/jobs/" + title.replace(' ', '-'));
        form.setLanguage("en");
        form.setJobType("permanent");
        return form;
    }

    // ------------------------------------------------------------- jobs

    /**
     * The seed's six include a DRAFT and an INACTIVE, so this is also the proof
     * that every row counts regardless of status.
     */
    @Test
    void aSeventhJobIsRefused() {
        assertThat(jobRepo.countByEmployerId(acme.getId())).isEqualTo(6);
        assertThatThrownBy(() -> jobService.save(jobForm("Seventh"), acme, false, "test"))
                .isInstanceOf(ValidationFailure.class)
                .hasMessageContaining("limit.jobs");
        assertThat(jobRepo.countByEmployerId(acme.getId())).isEqualTo(6);
    }

    @Test
    void deletingAJobMakesRoomAgain() {
        UUID victim = jobRepo.findByEmployerIdOrderByTitleAsc(acme.getId()).get(0).getId();
        jobService.delete(victim, admin);
        assertThatCode(() -> jobService.save(jobForm("Replacement"), acme, false, "test"))
                .doesNotThrowAnyException();
    }

    /** An edit is not a creation and must never be refused for being at the cap. */
    @Test
    void editingAnExistingJobIsNotRefusedAtTheCap() {
        var existing = jobRepo.findByEmployerIdOrderByTitleAsc(acme.getId()).get(0);
        JobForm form = JobForm.of(existing);
        form.setTitle(existing.getTitle() + " (edited)");
        assertThatCode(() -> jobService.save(form, acme, false, "test"))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------ feeds

    @Test
    void aThirdFeedIsRefused() {
        assertThat(feedRepo.countByEmployerId(acme.getId())).isEqualTo(2);
        assertThatThrownBy(() -> feedService.save(null, acme, "Marketing", null, null))
                .isInstanceOf(ValidationFailure.class)
                .hasMessageContaining("limit.feeds");
    }

    /**
     * Spec 3.5: every employer gets its {@code all} feed the moment it exists. The
     * exemption is structural - {@code createDefaultFeed} does not go through
     * {@code save} - and this test fails the moment someone routes it through.
     */
    @Test
    void theDefaultFeedIsNeverRefused() {
        Employer fresh = employerService.save(null, "Quota Co " + UUID.randomUUID(), null, null,
                null, locationRepo.findAll().get(0).getId(), admin);
        feedService.save(null, fresh, "One", null, null);
        feedService.save(null, fresh, "Two", null, null);
        assertThatThrownBy(() -> feedService.save(null, fresh, "Three", null, null))
                .isInstanceOf(ValidationFailure.class);

        assertThatCode(() -> feedService.createDefaultFeed(fresh)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------ invitations

    @Test
    void aSecondPendingInvitationIsRefused() {
        assertThatThrownBy(() -> invitationService.invite(
                acme.getId(), "member@example.com", MembershipRole.EDITOR, admin))
                .isInstanceOf(ValidationFailure.class)
                .hasMessageContaining("limit.invitations");
    }

    /**
     * The placement guard. Checked after the address lookup instead of before it,
     * an employer at its cap would answer SENT for an unregistered address and
     * refuse a registered one - rebuilding the account oracle spec 2.6 forbids.
     * Both addresses must be refused identically.
     */
    @Test
    void theInvitationRefusalDoesNotRevealWhetherTheAddressHasAnAccount() {
        assertThatThrownBy(() -> invitationService.invite(
                acme.getId(), "member@example.com", MembershipRole.EDITOR, admin))
                .isInstanceOf(ValidationFailure.class)
                .hasMessageContaining("limit.invitations");
        assertThatThrownBy(() -> invitationService.invite(
                acme.getId(), "nobody-at-all@example.com", MembershipRole.EDITOR, admin))
                .isInstanceOf(ValidationFailure.class)
                .hasMessageContaining("limit.invitations");
    }

    @Test
    void revokingAPendingInvitationMakesRoomAgain() {
        UUID pending = invitationRepo.findByEmployerIdAndStatusOrderByCreatedAtDesc(
                acme.getId(), InvitationStatus.PENDING).get(0).getId();
        invitationService.revoke(pending, admin);
        assertThatCode(() -> invitationService.invite(
                acme.getId(), "member@example.com", MembershipRole.EDITOR, admin))
                .doesNotThrowAnyException();
    }

    // ----------------------------------------------------------- tokens

    @Test
    void aSecondTokenIsRefused() {
        assertThatThrownBy(() -> tokenService.create(acme.getId(), "second",
                MembershipRole.EDITOR, Set.of(TokenScope.JOBS_READ), admin))
                .isInstanceOf(ValidationFailure.class)
                .hasMessageContaining("limit.tokens");
    }

    /** Revoking keeps the row (spec 3.10) but must free the quota. */
    @Test
    void aRevokedTokenNoLongerCounts() {
        long before = tokenRepo.countByEmployerIdAndRevokedAtIsNull(acme.getId());
        assertThat(before).isPositive();
        // Every live one, not just the newest: another test class may have left
        // a token behind, and the point here is the revoked/live distinction.
        tokenRepo.findByEmployerIdOrderByCreatedAtDesc(acme.getId()).stream()
                .filter(t -> !t.isRevoked())
                .forEach(t -> tokenService.revoke(t.getId(), admin));
        assertThat(tokenRepo.countByEmployerIdAndRevokedAtIsNull(acme.getId())).isZero();

        assertThatCode(() -> tokenService.create(acme.getId(), "replacement",
                MembershipRole.EDITOR, Set.of(TokenScope.JOBS_READ), admin))
                .doesNotThrowAnyException();
        assertThat(tokenRepo.findByEmployerIdOrderByCreatedAtDesc(acme.getId()))
                .as("revoked rows are retained, so the register still resolves")
                .hasSizeGreaterThan((int) before);
    }

    /**
     * An expired token still occupies a slot, because it is one click from live
     * again (spec 8.4). Only revoking means "gone", which is what makes revoking
     * the way back under the quota - and what stops an employer holding ten live
     * tokens plus a reserve of lapsed ones to flip between.
     */
    @Test
    void anExpiredTokenStillCountsBecauseItCanBeRenewed() {
        ServiceToken live = tokenRepo.findByEmployerIdOrderByCreatedAtDesc(acme.getId()).stream()
                .filter(t -> !t.isRevoked()).findFirst().orElseThrow();
        live.setExpiresAt(java.time.Instant.now().minusSeconds(60));
        tokenRepo.save(live);

        assertThatThrownBy(() -> tokenService.create(acme.getId(), "after-expiry",
                MembershipRole.EDITOR, Set.of(TokenScope.JOBS_READ), admin))
                .isInstanceOf(ValidationFailure.class)
                .hasMessageContaining("limit.tokens");
    }

    /**
     * And renewing one is never refused for being at the cap: the quota governs
     * creating, not repairing something that already exists (spec 8.4).
     */
    @Test
    void renewingAtTheCapIsNotRefused() {
        ServiceToken live = tokenRepo.findByEmployerIdOrderByCreatedAtDesc(acme.getId()).stream()
                .filter(t -> !t.isRevoked()).findFirst().orElseThrow();
        live.setExpiresAt(java.time.Instant.now().minusSeconds(60));
        tokenRepo.save(live);

        assertThatCode(() -> tokenService.renew(live.getId(), admin)).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------- members

    /**
     * Acme has two people and a token. If the token's membership counted, the
     * cap would fire one member early, so this pins the exclusion.
     */
    @Test
    void aTokensMembershipIsNotAMember() {
        ServiceToken seeded = tokenRepo.findByEmployerIdOrderByCreatedAtDesc(acme.getId()).get(0);
        assertThat(membershipRepo.findByServiceTokenIdAndEmployerId(
                seeded.getId(), acme.getId()))
                .as("the token does hold a membership of this employer")
                .isPresent();
        assertThat(membershipRepo.countByEmployerIdAndUserIsNotNull(acme.getId()))
                .as("but the member count is people only")
                .isEqualTo(2);
    }

}
