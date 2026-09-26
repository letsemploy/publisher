package org.letsemploy.ojobpub_publisher.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.invitation.InvitationRepo;
import org.letsemploy.ojobpub_publisher.invitation.InvitationService;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Real sign-in, without the development bypass (spec 2.2).
 *
 * <p>Every other back-office test runs under {@code dev}, which never reaches
 * this code. That is how the back-office chain once stayed closed with a provider
 * configured - nobody could have signed in - and not one test noticed.
 *
 * <p>The provider is configured the way a deployment configures it, through
 * {@code spring.security.oauth2.client.*}, so the auto-configured registration
 * repository is what the chain sees. Its endpoints are never called: {@code
 * oidcLogin()} stands in for the round trip to the provider.
 *
 * <p>Not {@code @Transactional}: an account is created by one request and looked
 * up by the next, as in production. What these tests create is removed after
 * each, per the convention in CLAUDE.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.WithoutDev.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.oidc.client-id=publisher",
        "spring.security.oauth2.client.registration.oidc.client-secret=secret",
        "spring.security.oauth2.client.registration.oidc.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.oidc.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.oidc.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.provider.oidc.authorization-uri=" + OidcLoginTest.IDP + "/auth",
        "spring.security.oauth2.client.provider.oidc.token-uri=" + OidcLoginTest.IDP + "/token",
        "spring.security.oauth2.client.provider.oidc.jwk-set-uri=" + OidcLoginTest.IDP + "/jwks",
        "spring.security.oauth2.client.provider.oidc.user-name-attribute=sub"})
class OidcLoginTest {

    static final String IDP = "https://idp.example";
    private static final String END_SESSION = IDP + "/logout";

    /** Fixed ids from the seed: Acme, and the dev admin who owns it. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID DEV_ADMIN = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String FEED = "/ojobpub/v1/acme-ag_003d6aec-021b-11f1-aefa-f649a5d91690"
            + "/all_cd58289d-022e-4a1e-df83-7e5f60718293/ojobpub.json";

    /**
     * Adds the end-session endpoint a discovered provider would advertise. The
     * properties above cannot express it - it only ever comes from discovery -
     * and RP-initiated logout needs it (spec 2.2).
     */
    @TestConfiguration
    static class ProviderMetadata {
        @Bean
        static BeanPostProcessor endSessionEndpoint() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof ClientRegistrationRepository repository) {
                        ClientRegistration oidc = repository.findByRegistrationId("oidc");
                        return new InMemoryClientRegistrationRepository(
                                ClientRegistration.withClientRegistration(oidc)
                                        .providerConfigurationMetadata(
                                                Map.of("end_session_endpoint", END_SESSION))
                                        .build());
                    }
                    return bean;
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private ClientRegistrationRepository registrations;
    @Autowired
    private InvitationService invitationService;
    @Autowired
    private InvitationRepo invitationRepo;
    @Autowired
    private MembershipService membershipService;

    @AfterEach
    void removeTheAccountsTheseTestsCreated() {
        // Invitations and memberships cascade with the user.
        userRepo.findAll().stream()
                .filter(u -> IDP.equals(u.getIssuer()))
                .forEach(userRepo::delete);
    }

    /** A sign-in as {@code subject}, with the claims a provider would send. */
    private OidcLoginRequestPostProcessor signedIn(String subject, String name, String email,
                                                   boolean verified) {
        return oidcLogin()
                .clientRegistration(registrations.findByRegistrationId("oidc"))
                .idToken(token -> token
                        .issuer(IDP)
                        .subject(subject)
                        .claim("name", name)
                        .claim("email", email)
                        .claim("email_verified", verified));
    }

    private UserEntity account(String subject) {
        return userRepo.findByIssuerAndSubject(IDP, subject).orElseThrow();
    }

    // ---------------------------------------------------------------- chains

    /**
     * The back-office asks for a sign-in on our own page, and that page hands over
     * to the provider. It must never answer as the locked chain would.
     */
    @Test
    void anAnonymousVisitorIsShownTheSignInPage() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ------------------------------------------------------------ sign-in page

    private String page(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * A standalone page: no shell, since a signed-out visitor has no sidebar or
     * user menu, and one way forward - to the provider. No password field: the
     * application must never handle one (spec 2.2, 7.19).
     */
    @Test
    void theSignInPageHandsOverToTheProvider() throws Exception {
        assertThat(page("/login"))
                .contains("Sign in to oJobPub Publisher")
                .contains("href=\"/oauth2/authorization/oidc\"")
                .doesNotContain("<aside")
                .doesNotContain("href=\"/jobs\"")
                .doesNotContain("type=\"password\"")
                .doesNotContain("??");
    }

    /**
     * One provider, configured without a client-name: exactly one button, the
     * primary one, saying a plain "Sign in" - the page as it was before several
     * providers were possible (spec 7.19).
     */
    @Test
    void aSingleProviderGetsOnePrimarySignInButton() throws Exception {
        String body = page("/login");
        assertThat(body.split("/oauth2/authorization/", -1)).hasSize(2);
        assertThat(body).contains("btn-primary").contains(">Sign in</span>")
                .doesNotContain("Continue with");
    }

    /** The strict CSP of spec 9.4 would break anything inline, so nothing is. */
    @Test
    void theSignInPageHasNothingInline() throws Exception {
        String body = page("/login");
        assertThat(body).doesNotContain("<style").doesNotContainPattern("<script(?![^>]*\\ssrc=)");
    }

    /** Spring's generated page is gone: a failed sign-in lands here, told what happened. */
    @Test
    void aFailedSignInIsExplainedOnTheSignInPage() throws Exception {
        assertThat(page("/login?error"))
                .contains("Sign-in did not complete")
                .contains("role=\"alert\"");
    }

    @Test
    void signingOutEndsOnTheSignInPageSayingSo() throws Exception {
        assertThat(page("/login?logout")).contains("You have signed out");
    }

    /** German too, and the notice survives the switch (spec 8.1). */
    @Test
    void theSignInPageSpeaksGerman() throws Exception {
        assertThat(page("/login?error&lang=de"))
                .contains("Bei oJobPub Publisher anmelden")
                .contains("Die Anmeldung wurde nicht abgeschlossen")
                .contains("lang=\"de\"");
    }

    @Test
    void aSignedInVisitorIsNotShownTheSignInPage() throws Exception {
        mvc.perform(get("/login").with(signedIn("alice-5", "Alice", "alice5@example.com", true)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    /** It was missing from the public paths, so no signed-out page could load it. */
    @Test
    void theApplicationScriptIsPublic() throws Exception {
        mvc.perform(get("/js/app.js")).andExpect(status().isOk());
    }

    /** PKCE is required even for this confidential client (spec 2.2). */
    @Test
    void theAuthorizationRequestCarriesAPkceChallenge() throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/oidc"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location)
                .startsWith(IDP + "/auth")
                .contains("code_challenge=")
                .contains("code_challenge_method=S256");
    }

    /** Machines read the feed; it must never see a login page (spec 2.4). */
    @Test
    void theFeedStaysPublic() throws Exception {
        mvc.perform(get(FEED)).andExpect(status().isOk());
    }

    // --------------------------------------------------------------- accounts

    /**
     * The first sign-in creates the account: the User platform role and no
     * memberships, so the new account sees an empty state rather than anybody's
     * employer (spec 2.2).
     */
    @Test
    void theFirstSignInCreatesAnAccountWithNoStanding() throws Exception {
        mvc.perform(get("/").with(signedIn("alice-1", "Alice Anders", "alice@example.com", true)))
                .andExpect(status().isOk());

        UserEntity alice = account("alice-1");
        assertThat(alice.getRole()).isEqualTo(UserEntity.Role.USER);
        assertThat(alice.getDisplayName()).isEqualTo("Alice Anders");
        assertThat(alice.getEmail()).isEqualTo("alice@example.com");
        assertThat(membershipService.of(alice.getId())).isEmpty();
    }

    /** An account created by sign-in is not silently given access (spec 2.4). */
    @Test
    void aNewAccountCannotSeeAnExistingEmployer() throws Exception {
        mvc.perform(get("/employers/" + ACME)
                        .with(signedIn("alice-2", "Alice", "alice2@example.com", true)))
                .andExpect(status().isNotFound());
    }

    /**
     * Name and email are the provider's: a later sign-in brings the change here
     * (spec 2.2). The account is found by issuer and subject, so it stays one row.
     */
    @Test
    void aLaterSignInRefreshesNameAndEmail() throws Exception {
        mvc.perform(get("/").with(signedIn("alice-3", "Alice Anders", "alice@example.com", true)));
        UUID id = account("alice-3").getId();

        mvc.perform(get("/").with(signedIn("alice-3", "Alice Brun", "alice.brun@example.com", true)))
                .andExpect(status().isOk());

        UserEntity alice = account("alice-3");
        assertThat(alice.getId()).isEqualTo(id);
        assertThat(alice.getDisplayName()).isEqualTo("Alice Brun");
        assertThat(alice.getEmail()).isEqualTo("alice.brun@example.com");
    }

    /**
     * An unverified email is not kept, so it cannot draw invitations addressed to
     * someone else. Inviting it answers exactly as for an unknown address (spec
     * 2.2, 2.6).
     */
    @Test
    void anUnverifiedEmailIsNotKeptAndCannotBeInvited() throws Exception {
        mvc.perform(get("/").with(signedIn("mallory-1", "Mallory", "victim@example.com", false)))
                .andExpect(status().isOk());

        UserEntity mallory = account("mallory-1");
        assertThat(mallory.getEmail()).isNull();

        Actor owner = Actor.user(DEV_ADMIN, "dev@localhost", "dev@localhost", true,
                Map.of(ACME, MembershipRole.OWNER));
        assertThat(invitationService.invite(ACME, "victim@example.com", MembershipRole.EDITOR, owner))
                .isEqualTo(InvitationService.InviteOutcome.SENT);
        assertThat(invitationRepo.findAll())
                .noneMatch(i -> i.getInvitee().getId().equals(mallory.getId()));
    }

    // ----------------------------------------------------------------- logout

    /**
     * Logout ends the provider's session too, and asks it to send the browser
     * back to the sign-in page, which says so (spec 2.2, 7.19).
     */
    @Test
    void logoutEndsTheSessionAtTheProvider() throws Exception {
        String location = mvc.perform(post("/logout")
                        .with(signedIn("alice-4", "Alice", "alice4@example.com", true))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader("Location");
        assertThat(URLDecoder.decode(location, StandardCharsets.UTF_8))
                .startsWith(END_SESSION)
                .contains("id_token_hint=")
                .contains("post_logout_redirect_uri=http://localhost/login?logout");
    }
}
