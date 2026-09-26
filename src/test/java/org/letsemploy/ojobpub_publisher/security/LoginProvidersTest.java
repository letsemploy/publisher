package org.letsemploy.ojobpub_publisher.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private MockMvc mvc;

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
}
