package org.letsemploy.ojobpub_publisher.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.account.AccountToken;
import org.letsemploy.ojobpub_publisher.account.AccountTokenRepo;
import org.letsemploy.ojobpub_publisher.account.LocalAccount;
import org.letsemploy.ojobpub_publisher.account.LocalAccountRepo;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditRepo;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.employer.Headquarters;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.membership.MembershipRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.letsemploy.ojobpub_publisher.security.Impersonation;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.letsemploy.ojobpub_publisher.token.ServiceTokenRepo;
import org.letsemploy.ojobpub_publisher.token.ServiceTokenService;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting one's own account (spec 2.13): employers only they own go with it,
 * everything else they belonged to stays, the tokens they created keep working,
 * the email must be typed back, and the only admin may not.
 *
 * <p>Transactional like {@code EmployerDeleteTest}: the cascades it checks are the
 * database's, and run inside the test transaction on both databases.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class AccountDeletionTest {

    /** From the seed: Acme, and the dev admin - the only admin there is. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID DEV = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AccountDeletionService service;
    @Autowired
    private EmployerService employerService;
    @Autowired
    private EmployerRepo employerRepo;
    @Autowired
    private FeedService feedService;
    @Autowired
    private MembershipService membershipService;
    @Autowired
    private MembershipRepo membershipRepo;
    @Autowired
    private ServiceTokenService tokens;
    @Autowired
    private ServiceTokenRepo tokenRepo;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private LocalAccountRepo localAccounts;
    @Autowired
    private AccountTokenRepo accountTokens;
    @Autowired
    private AuditRepo auditRepo;
    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private CurrentUserService currentUserService;

    private UserEntity leaver;
    private UserEntity stayer;
    private Employer solo;
    private Employer shared;
    private Actor actor;
    private ServiceTokenService.CreatedToken token;

    /**
     * The leaver owns Solo Ltd alone, owns Shared Ltd with the stayer - and made an
     * API token there - and is an editor of Acme.
     */
    @BeforeEach
    void aPersonWithSomethingToLose() {
        leaver = user("leaver", "leaver@example.com", UserEntity.Role.USER);
        stayer = user("stayer", "stayer@example.com", UserEntity.Role.USER);

        solo = employerService.save(null, "Solo Ltd", null, null, null,
                Headquarters.newLocation("Winterthur", "CH"), plain(leaver));
        feedService.createDefaultFeed(solo);
        shared = employerService.save(null, "Shared Ltd", null, null, null,
                Headquarters.newLocation("Basel", "CH"), plain(stayer));
        membershipService.grant(leaver, shared, MembershipRole.OWNER);
        membershipService.grant(leaver, employerRepo.findById(ACME).orElseThrow(), MembershipRole.EDITOR);

        actor = Actor.user(leaver.getId(), "Lea Leaver", leaver.getEmail(), false,
                Map.of(solo.getId(), MembershipRole.OWNER, shared.getId(), MembershipRole.OWNER,
                        ACME, MembershipRole.EDITOR));
        token = tokens.create(shared.getId(), "ci", MembershipRole.EDITOR, Set.of(TokenScope.JOBS_READ), actor);
        actAs(actor);
    }

    private UserEntity user(String name, String email, UserEntity.Role role) {
        UserEntity user = new UserEntity();
        user.setIssuer("test");
        user.setSubject(name + "-" + UUID.randomUUID());
        user.setEmail(email);
        user.setDisplayName(name);
        user.setRole(role);
        return userRepo.save(user);
    }

    private static Actor plain(UserEntity user) {
        return Actor.user(user.getId(), user.getDisplayName(), user.getEmail(), false, Map.of());
    }

    private void actAs(Actor actor) {
        given(currentUserService.isDevMode()).willReturn(true);
        given(currentUserService.current()).willReturn(actor);
    }

    private String confirmation() throws Exception {
        return mvc.perform(get("/account/delete")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private List<AuditEvent> events(AuditAction action) {
        return auditRepo.findAll().stream().filter(e -> e.getAction() == action).toList();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void theConfirmationSaysWhatGoesWhatStaysAndWhatToType() throws Exception {
        String html = confirmation();
        String deleted = html.substring(html.indexOf("id=\"deleted-employers\""), html.indexOf("id=\"left-employers\""));
        assertThat(deleted).contains("Solo Ltd").doesNotContain("Shared Ltd");
        assertThat(html.substring(html.indexOf("id=\"left-employers\"")))
                .contains("Shared Ltd").contains("Acme");
        assertThat(html)
                .contains("data-confirm-name=\"leaver@example.com\"")
                .contains("name=\"confirmEmail\"")
                .contains("public feed URLs")
                .doesNotContain("id=\"only-admin\"");
    }

    @Test
    void aWrongOrMissingEmailDeletesNothing() throws Exception {
        assertThat(mvc.perform(post("/account/delete").param("confirmEmail", "someone@example.com"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .contains("Nothing was deleted");
        mvc.perform(post("/account/delete")).andExpect(status().isOk());
        flushAndClear();

        assertThat(userRepo.findById(leaver.getId())).isPresent();
        assertThat(employerRepo.findById(solo.getId())).isPresent();
    }

    @Test
    void theAccountGoesWithTheEmployersOnlyItOwned() throws Exception {
        // Case and surrounding space are forgiven; the address itself is not.
        mvc.perform(post("/account/delete").param("confirmEmail", "  Leaver@Example.com "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?deleted"));
        flushAndClear();

        assertThat(userRepo.findById(leaver.getId())).isEmpty();
        assertThat(employerRepo.findById(solo.getId())).isEmpty();
        assertThat(feedService.findByEmployer(solo.getId())).isEmpty();
        // Someone else still owns these: they stay, without the leaver.
        assertThat(employerRepo.findById(shared.getId())).isPresent();
        assertThat(employerRepo.findById(ACME)).isPresent();
        assertThat(membershipRepo.findByUserId(leaver.getId())).isEmpty();
        assertThat(membershipRepo.findByUserId(stayer.getId())).hasSize(1);

        assertThat(events(AuditAction.EMPLOYER_DELETED))
                .anySatisfy(e -> assertThat(e.getTargetId()).isEqualTo(solo.getId().toString()));
        assertThat(events(AuditAction.ACCOUNT_DELETED))
                .singleElement().satisfies(e -> assertThat(e.getSubjectUserId()).isEqualTo(leaver.getId()));
    }

    /** Tokens belong to the employer (spec 2.8): the integration outlives whoever set it up. */
    @Test
    void aTokenTheyCreatedKeepsWorkingAndForgetsItsCreator() throws Exception {
        service.delete(actor, "leaver@example.com");
        flushAndClear();

        assertThat(tokenRepo.findById(token.token().getId())).hasValueSatisfying(
                t -> assertThat(t.getCreatedBy()).isNull());
        assertThat(tokens.authenticate(token.secret())).isPresent();

        actAs(Actor.user(stayer.getId(), "stayer", stayer.getEmail(), false,
                Map.of(shared.getId(), MembershipRole.OWNER)));
        assertThat(mvc.perform(get("/tokens")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .contains("Created by a deleted account");
    }

    @Test
    void aLocalAccountTakesItsPasswordAndLinksWithIt() {
        LocalAccount account = new LocalAccount();
        account.setEmail("local-leaver@example.com");
        account.setDisplayName("Local Leaver");
        account.setPasswordHash("{noop}irrelevant");
        account.setVerifiedAt(Instant.now());
        account.setPasswordChangedAt(Instant.now());
        account = localAccounts.save(account);
        AccountToken link = new AccountToken();
        link.setAccount(account);
        link.setPurpose(AccountToken.Purpose.RESET_PASSWORD);
        link.setTokenHash("hash-" + UUID.randomUUID());
        link.setExpiresAt(Instant.now().plusSeconds(3600));
        link = accountTokens.save(link);

        UserEntity user = new UserEntity();
        user.setIssuer(LocalAccount.ISSUER);
        user.setSubject(account.getId().toString());
        user.setEmail(account.getEmail());
        user.setDisplayName(account.getDisplayName());
        user = userRepo.save(user);
        flushAndClear();

        service.delete(plain(user), "local-leaver@example.com");
        flushAndClear();

        assertThat(userRepo.findById(user.getId())).isEmpty();
        assertThat(localAccounts.findById(account.getId())).isEmpty();
        assertThat(accountTokens.findById(link.getId())).isEmpty();
    }

    @Test
    void theOnlyAdminMayNot() throws Exception {
        Actor dev = Actor.user(DEV, "dev@localhost", "dev@localhost", true, Map.of(ACME, MembershipRole.OWNER));
        actAs(dev);
        assertThat(confirmation()).contains("id=\"only-admin\"").doesNotContain("name=\"confirmEmail\"");
        assertThatThrownBy(() -> service.delete(dev, "dev@localhost"))
                .isInstanceOfSatisfying(ValidationFailure.class,
                        e -> assertThat(e.fields()).containsExactly("admin"));

        // A suspended admin cannot act, so is not the other admin.
        UserEntity suspended = user("suspended-admin", "sa@example.com", UserEntity.Role.ADMIN);
        suspended.setSuspendedAt(Instant.now());
        userRepo.save(suspended);
        assertThatThrownBy(() -> service.delete(dev, "dev@localhost")).isInstanceOf(ValidationFailure.class);
        assertThat(userRepo.findById(DEV)).isPresent();
    }

    @Test
    void anAdminMayWhenAnotherRemains() {
        UserEntity admin = user("second-admin", "admin2@example.com", UserEntity.Role.ADMIN);
        service.delete(plain(admin), "admin2@example.com");
        flushAndClear();
        assertThat(userRepo.findById(admin.getId())).isEmpty();
        assertThat(userRepo.findById(DEV)).isPresent();
    }

    @Test
    void anAccountWithoutAnEmailTypesItsName() {
        UserEntity nameless = user("No Address", null, UserEntity.Role.USER);
        assertThat(service.preview(plain(nameless)).confirmWith()).isEqualTo("No Address");
        service.delete(plain(nameless), "no address");
        flushAndClear();
        assertThat(userRepo.findById(nameless.getId())).isEmpty();
    }

    @Test
    void aServiceTokenNeverMay() {
        Actor credential = Actor.serviceToken(UUID.randomUUID(), "ci", shared.getId(), MembershipRole.OWNER,
                Set.of(TokenScope.PEOPLE_WRITE));
        assertThatThrownBy(() -> service.delete(credential, "leaver@example.com"))
                .isInstanceOf(NotFoundException.class);
    }

    /** Viewing as someone is read-only (spec 2.9): neither the screen nor the act. */
    @Test
    void notWhileViewingAsSomeone() throws Exception {
        given(currentUserService.isImpersonating()).willReturn(true);
        mvc.perform(get("/account/delete")).andExpect(status().isNotFound());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(Impersonation.class.getName(), new Impersonation.State(DEV, leaver.getId()));
        mvc.perform(post("/account/delete").session(session).param("confirmEmail", "leaver@example.com"))
                .andExpect(status().isSeeOther());
        flushAndClear();
        assertThat(userRepo.findById(leaver.getId())).isPresent();
    }

    @Test
    void theSignInPageSaysItIsDone() throws Exception {
        given(currentUserService.isDevMode()).willReturn(false);
        given(currentUserService.current()).willReturn(Actor.anonymous());
        assertThat(mvc.perform(get("/login?deleted")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .contains("id=\"account-deleted\"")
                .contains("Your account has been deleted.");
    }
}
