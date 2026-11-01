package org.letsemploy.ojobpub_publisher.welcome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The Welcome page (spec 7.30), through real sign-ins: the one that creates the
 * account lands there instead of on the dashboard, every later one does not,
 * and nothing about it is stored.
 *
 * <p>Without the dev bypass, which never creates an account. Not
 * {@code @Transactional}: the account is created by one request and found by the
 * next, as in production. The accounts are removed after each test.
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
        "spring.security.oauth2.client.provider.oidc.authorization-uri=" + WelcomeTest.IDP + "/auth",
        "spring.security.oauth2.client.provider.oidc.token-uri=" + WelcomeTest.IDP + "/token",
        "spring.security.oauth2.client.provider.oidc.jwk-set-uri=" + WelcomeTest.IDP + "/jwks",
        "spring.security.oauth2.client.provider.oidc.user-name-attribute=sub"})
class WelcomeTest {

    static final String IDP = "https://welcome-idp.example";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private ClientRegistrationRepository registrations;

    @AfterEach
    void removeTheAccountsTheseTestsCreated() {
        userRepo.findAll().stream().filter(u -> IDP.equals(u.getIssuer())).forEach(userRepo::delete);
    }

    private OidcLoginRequestPostProcessor as(String subject) {
        return oidcLogin()
                .clientRegistration(registrations.findByRegistrationId("oidc"))
                .idToken(t -> t.issuer(IDP).subject(subject)
                        .claim("email", subject + "@example.com").claim("email_verified", true));
    }

    @Test
    void theSignInThatCreatesTheAccountLandsOnTheWelcome() throws Exception {
        mvc.perform(get("/").with(as("newcomer")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/welcome"));
        assertThat(userRepo.findByIssuerAndSubject(IDP, "newcomer")).isPresent();
    }

    /** Nothing is stored: the next visit is the dashboard, and the page is in the menu. */
    @Test
    void everyLaterSignInLandsOnTheDashboard() throws Exception {
        mvc.perform(get("/").with(as("returning")));

        String dashboard = mvc.perform(get("/").with(as("returning")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(dashboard).contains("href=\"/welcome\"");
    }

    /** An account from before the Welcome existed is never sent to it. */
    @Test
    void anExistingAccountIsNotSentThere() throws Exception {
        UserEntity existing = new UserEntity();
        existing.setIssuer(IDP);
        existing.setSubject("veteran");
        existing.setEmail("veteran@example.com");
        existing.setDisplayName("veteran@example.com");
        existing.setRole(UserEntity.Role.USER);
        userRepo.save(existing);

        mvc.perform(get("/").with(as("veteran"))).andExpect(status().isOk());
    }

    /** With no employer, steps 2-4 point at step 1 instead of at screens (spec 7.30). */
    @Test
    void withoutAnEmployerTheStepsPointAtTheFirst() throws Exception {
        String body = mvc.perform(get("/welcome").with(as("empty-handed")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String steps = body.substring(body.indexOf("id=\"step-jobs\""), body.indexOf("id=\"step-help\""));
        assertThat(steps).contains("href=\"#step-employer\"")
                .doesNotContain("href=\"/jobs\"")
                .doesNotContain("href=\"/feeds\"")
                .doesNotContain("href=\"/people\"")
                .doesNotContain("href=\"/tokens\"");
        assertThat(body).contains("href=\"/employers/create\"").contains("href=\"/invitations\"");
    }
}
