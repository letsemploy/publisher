package org.letsemploy.ojobpub_publisher.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditRepo;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.membership.Membership;
import org.letsemploy.ojobpub_publisher.membership.MembershipRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suspending and reinstating an account from the Users screen (spec 2.11, 7.20):
 * who may, on whom, and what is recorded. The other half - that a suspended
 * account really is shut out - needs the real sign-in path, so it is in
 * {@code OidcLoginTest}.
 *
 * <p>The actor is the seeded admin, in admin mode, as for every back-office test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class AccountSuspensionTest {

    /** Fixed ids from the seed. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID DEV_ADMIN = UUID.fromString("11111111-1111-4111-8111-111111111111");
    /** An editor of Acme. */
    private static final UUID MARA = UUID.fromString("44444444-4444-4444-8444-444444444444");
    /** No membership. */
    private static final UUID EDITH = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private AuditRepo auditRepo;
    @Autowired
    private MembershipRepo membershipRepo;
    @Autowired
    private UserService userService;

    private String page(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private List<AuditEvent> events(AuditAction action, UUID subject) {
        return auditRepo.findAll().stream()
                .filter(e -> e.getAction() == action && subject.equals(e.getSubjectUserId()))
                .toList();
    }

    private boolean suspended(UUID userId) {
        return userRepo.findById(userId).orElseThrow().isSuspended();
    }

    @Test
    void anAdminSuspendsAnAccountAndItIsRecorded() throws Exception {
        mvc.perform(post("/users/" + MARA + "/suspend").param("reason", "Posting spam"))
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("successMsg", "users.suspended"));

        assertThat(suspended(MARA)).isTrue();
        assertThat(events(AuditAction.ACCOUNT_SUSPENDED, MARA)).singleElement().satisfies(e -> {
            assertThat(e.getActorUserId()).isEqualTo(DEV_ADMIN);
            assertThat(e.getDetail()).isEqualTo("Posting spam");
            // The person's own log, not an employer's (spec 3.12).
            assertThat(e.getEmployerId()).isNull();
        });
        // The membership stays, to come back with a reinstatement.
        assertThat(membershipRepo.findByUserIdAndEmployerId(MARA, ACME)).isPresent();
    }

    @Test
    void theUsersScreenSaysWhoIsSuspendedAndOffersReinstating() throws Exception {
        assertThat(page("/users")).contains("/users/" + MARA + "/suspend");
        mvc.perform(post("/users/" + MARA + "/suspend"));

        String suspendedOnly = page("/users?suspended=true");
        assertThat(suspendedOnly).contains("Suspended since")
                .contains("/users/" + MARA + "/reinstate")
                .doesNotContain("/users/" + MARA + "/suspend")
                // The filter leaves out everyone active.
                .doesNotContain("/users/" + EDITH + "/");
    }

    @Test
    void reinstatingLiftsTheSuspension() throws Exception {
        mvc.perform(post("/users/" + MARA + "/suspend"));
        mvc.perform(post("/users/" + MARA + "/reinstate"))
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("successMsg", "users.reinstated"));

        assertThat(suspended(MARA)).isFalse();
        assertThat(events(AuditAction.ACCOUNT_REINSTATED, MARA)).hasSize(1);
    }

    /** Suspending twice, or reinstating an active account, changes nothing and records nothing. */
    @Test
    void aNoOpIsNotRecorded() throws Exception {
        mvc.perform(post("/users/" + MARA + "/suspend"));
        mvc.perform(post("/users/" + MARA + "/suspend"));
        mvc.perform(post("/users/" + EDITH + "/reinstate"));

        assertThat(events(AuditAction.ACCOUNT_SUSPENDED, MARA)).hasSize(1);
        assertThat(events(AuditAction.ACCOUNT_REINSTATED, EDITH)).isEmpty();
    }

    @Test
    void nobodySuspendsTheirOwnAccount() throws Exception {
        mvc.perform(get("/users/" + DEV_ADMIN + "/suspend"))
                .andExpect(redirectedUrl("/users"))
                .andExpect(flash().attribute("errorMsg", "users.suspend.notSelf"));
        mvc.perform(post("/users/" + DEV_ADMIN + "/suspend"))
                .andExpect(flash().attribute("errorMsg", "users.suspend.notSelf"));
        assertThat(suspended(DEV_ADMIN)).isFalse();
    }

    /** An admin loses the role first - in configuration, if that is where it came from. */
    @Test
    void anAdminCannotBeSuspended() throws Exception {
        UserEntity edith = userRepo.findById(EDITH).orElseThrow();
        edith.setRole(UserEntity.Role.ADMIN);
        userRepo.save(edith);

        mvc.perform(post("/users/" + EDITH + "/suspend"))
                .andExpect(flash().attribute("errorMsg", "users.suspend.notAdmin"));
        assertThat(suspended(EDITH)).isFalse();
        assertThat(page("/users")).doesNotContain("/users/" + EDITH + "/suspend");
    }

    /**
     * The last active owner of an employer can be suspended, but the confirmation
     * names what is left without one (spec 2.11).
     */
    @Test
    void theConfirmationNamesEmployersLeftWithoutAnActiveOwner() throws Exception {
        assertThat(page("/users/" + MARA + "/suspend")).doesNotContain("only active owner");

        Membership mara = membershipRepo.findByUserIdAndEmployerId(MARA, ACME).orElseThrow();
        mara.setRole(MembershipRole.OWNER);
        membershipRepo.save(mara);
        Membership admin = membershipRepo.findByUserIdAndEmployerId(DEV_ADMIN, ACME).orElseThrow();
        admin.setSuspendedAt(Instant.now());
        membershipRepo.save(admin);

        assertThat(page("/users/" + MARA + "/suspend")).contains("only active owner").contains("Acme");
    }

    /** A reason too long for the log is refused, with what was typed kept (spec 7.7). */
    @Test
    void anOverlongReasonIsRefusedWithTheInputKept() throws Exception {
        String reason = "x".repeat(UserService.MAX_REASON + 1);
        assertThat(mvc.perform(post("/users/" + MARA + "/suspend").param("reason", reason))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .contains(reason).contains("at most 200 characters");
        assertThat(suspended(MARA)).isFalse();
    }

    /** Anyone but an admin is told the accounts do not exist (spec 2.4). */
    @Test
    void onlyAnAdminMaySuspend() {
        Actor owner = Actor.user(MARA, "Mara", "member@example.com", false,
                Map.of(ACME, MembershipRole.OWNER));
        assertThatThrownBy(() -> userService.suspend(EDITH, null, owner))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> userService.reinstate(EDITH, owner))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> userService.suspension(EDITH, owner))
                .isInstanceOf(NotFoundException.class);
    }

    /** A suspended account can still be looked at, read-only (spec 2.9). */
    @Test
    void aSuspendedAccountCanStillBeViewedAs() throws Exception {
        mvc.perform(post("/users/" + MARA + "/suspend"));
        mvc.perform(post("/users/" + MARA + "/impersonate"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMsg", "impersonation.started"));
    }
}
