package org.letsemploy.ojobpub_publisher.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.invitation.InvitationRepo;
import org.letsemploy.ojobpub_publisher.invitation.InvitationService;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Several identity providers, one button each (spec 7.19).
 *
 * <p>Configured the way a deployment would: {@code google} with nothing but a
 * client id and secret, relying on Spring's built-in Google defaults, and a
 * company provider with its endpoints spelled out. No network is used - the page
 * only lists them, and the redirect is built locally.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.WithoutDev.class)
@TestPropertySource(properties = {
        // GitHub: Spring's built-in defaults plus the user:email scope it requires (spec 2.2).
        "spring.security.oauth2.client.registration.github.client-id=github-client",
        "spring.security.oauth2.client.registration.github.client-secret=secret",
        "spring.security.oauth2.client.registration.github.scope=read:user,user:email",
        "spring.security.oauth2.client.registration.google.client-id=google-client",
        "spring.security.oauth2.client.registration.google.client-secret=secret",
        "spring.security.oauth2.client.registration.acme.client-id=acme-client",
        "spring.security.oauth2.client.registration.acme.client-secret=secret",
        "spring.security.oauth2.client.registration.acme.client-name=Acme SSO",
        "spring.security.oauth2.client.registration.acme.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.acme.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.acme.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.provider.acme.authorization-uri=https://sso.acme.example/auth",
        "spring.security.oauth2.client.provider.acme.token-uri=https://sso.acme.example/token",
        "spring.security.oauth2.client.provider.acme.jwk-set-uri=https://sso.acme.example/jwks",
        "spring.security.oauth2.client.provider.acme.user-name-attribute=sub"})
class LoginProvidersTest {

    private static final String GITHUB = "https://github.com";
    private static final UUID ACME_EMPLOYER = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID DEV_ADMIN = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ClientRegistrationRepository registrations;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private InvitationService invitationService;
    @Autowired
    private InvitationRepo invitationRepo;

    /** Not @Transactional: an account made by one request is read by the next. */
    @AfterEach
    void removeTheGitHubAccounts() {
        userRepo.findAll().stream().filter(u -> GITHUB.equals(u.getIssuer())).forEach(userRepo::delete);
    }

    /** Signed in through GitHub, as GitHubUserService would have built the principal. */
    private OAuth2LoginRequestPostProcessor viaGitHub(long id, String login, String name,
                                                     String verifiedEmail) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", id);
        profile.put("login", login);
        profile.put("name", name);
        return oauth2Login()
                .clientRegistration(registrations.findByRegistrationId("github"))
                .oauth2User(new GitHubUser(List.of(new SimpleGrantedAuthority("OAUTH2_USER")), profile,
                        "id", GITHUB, verifiedEmail));
    }

    private String loginPage() throws Exception {
        return mvc.perform(get("/login")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * Each configured provider, each to its own authorisation start, sorted by
     * name. Configured before Acme, Google still comes after "Acme SSO": Boot
     * keeps the registrations in a hash map, so there is no configured order to
     * keep, and alphabetical is the order a person can predict.
     */
    @Test
    void everyProviderGetsAButtonSortedByName() throws Exception {
        String page = loginPage();
        int google = page.indexOf("href=\"/oauth2/authorization/google\"");
        int acme = page.indexOf("href=\"/oauth2/authorization/acme\"");
        assertThat(google).as("Google button").isPositive();
        assertThat(acme).as("Acme button").isPositive();
        assertThat(acme).as("Acme SSO before Google").isLessThan(google);
    }

    /**
     * Named by client-name, falling back to the brand for a well-known provider;
     * the logo only where the provider is recognised by its host.
     */
    @Test
    void buttonsAreNamedAndBrandedByProvider() throws Exception {
        String page = loginPage();
        assertThat(page)
                .contains("Continue with Google")
                .contains("Continue with Acme SSO")
                .contains("#ti-brand-google")
                .doesNotContain("#ti-brand-gitlab")
                .doesNotContain("#ti-brand-windows");
        String acmeButton = page.substring(page.indexOf("/oauth2/authorization/acme"));
        acmeButton = acmeButton.substring(0, acmeButton.indexOf("</a>"));
        assertThat(acmeButton).contains("#ti-user").doesNotContain("#ti-brand-");
    }

    /** With a choice to make, no provider is dressed as the obvious one. */
    @Test
    void noProviderIsPreferredWhenThereAreSeveral() throws Exception {
        assertThat(loginPage()).doesNotContain("btn-primary");
    }

    @Test
    void germanButtonsNameTheProviderToo() throws Exception {
        String page = mvc.perform(get("/login?lang=de")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Weiter mit Google").contains("Weiter mit Acme SSO");
    }

    /** Every provider gets the same authorisation request - PKCE included (spec 2.2). */
    @Test
    void eachButtonStartsItsOwnProvidersFlowWithPkce() throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/acme"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location)
                .startsWith("https://sso.acme.example/auth")
                .contains("client_id=acme-client")
                .contains("code_challenge_method=S256")
                .contains("redirect_uri=http://localhost/login/oauth2/code/acme");

        assertThat(mvc.perform(get("/oauth2/authorization/google"))
                .andReturn().getResponse().getHeader("Location"))
                .startsWith("https://accounts.google.com/")
                .contains("code_challenge_method=S256");
    }

    // ------------------------------------------------------------------ GitHub

    @Test
    void gitHubHasItsButtonAndLogo() throws Exception {
        String page = loginPage();
        assertThat(page).contains("href=\"/oauth2/authorization/github\"")
                .contains("Continue with GitHub")
                .contains("#ti-brand-github");
    }

    /**
     * An account keyed by github.com and the numeric id, never the login handle,
     * with the verified primary address (spec 2.2) - and a new, empty one: no
     * standing anywhere until they create an employer or are invited.
     */
    @Test
    void signingInWithGitHubCreatesAnAccountKeyedByTheNumericId() throws Exception {
        mvc.perform(get("/").with(viaGitHub(583231, "octocat", "The Octocat", "octo@example.com")))
                .andExpect(status().isOk());

        UserEntity octo = userRepo.findByIssuerAndSubject(GITHUB, "583231").orElseThrow();
        assertThat(octo.getDisplayName()).isEqualTo("The Octocat");
        assertThat(octo.getEmail()).isEqualTo("octo@example.com");
        assertThat(octo.getRole()).isEqualTo(UserEntity.Role.USER);
    }

    /** A renamed handle or a changed address is the same person: the same row. */
    @Test
    void aLaterGitHubSignInRefreshesTheSameAccount() throws Exception {
        mvc.perform(get("/").with(viaGitHub(42, "old-handle", null, "a@example.com")));
        UUID id = userRepo.findByIssuerAndSubject(GITHUB, "42").orElseThrow().getId();

        mvc.perform(get("/").with(viaGitHub(42, "new-handle", null, "b@example.com")))
                .andExpect(status().isOk());

        UserEntity same = userRepo.findByIssuerAndSubject(GITHUB, "42").orElseThrow();
        assertThat(same.getId()).isEqualTo(id);
        assertThat(same.getDisplayName()).as("no name, so the login").isEqualTo("new-handle");
        assertThat(same.getEmail()).isEqualTo("b@example.com");
    }

    /**
     * Without a verified address there is nothing to invite: the invite form
     * answers as it does for an unknown address (spec 2.6).
     */
    @Test
    void aGitHubUserWithoutAVerifiedAddressCannotBeInvited() throws Exception {
        mvc.perform(get("/").with(viaGitHub(7, "nomail", "No Mail", null))).andExpect(status().isOk());
        UserEntity user = userRepo.findByIssuerAndSubject(GITHUB, "7").orElseThrow();
        assertThat(user.getEmail()).isNull();

        Actor owner = Actor.user(DEV_ADMIN, "dev@localhost", "dev@localhost", true,
                Map.of(ACME_EMPLOYER, MembershipRole.OWNER));
        assertThat(invitationService.invite(ACME_EMPLOYER, "nomail@example.com", MembershipRole.EDITOR, owner))
                .isEqualTo(InvitationService.InviteOutcome.SENT);
        assertThat(invitationRepo.findAll()).noneMatch(i -> i.getInvitee().getId().equals(user.getId()));
    }

    /** GitHub gets the same authorisation request as every other provider, PKCE included. */
    @Test
    void theGitHubButtonAsksForTheEmailScope() throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/github"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader("Location");
        assertThat(location)
                .startsWith("https://github.com/login/oauth/authorize")
                .contains("scope=read:user%20user:email")
                .contains("code_challenge_method=S256")
                .contains("redirect_uri=http://localhost/login/oauth2/code/github");
    }
}
