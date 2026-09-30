package org.letsemploy.ojobpub_publisher.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
 * An identity provider without local accounts (spec 2.12, 7.24): no form on the sign-in
 * page, and the account screens are not there to reach.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.WithoutDev.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.acme.client-id=publisher",
        "spring.security.oauth2.client.registration.acme.client-secret=secret",
        "spring.security.oauth2.client.registration.acme.client-name=Acme SSO",
        "spring.security.oauth2.client.registration.acme.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.acme.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.acme.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.provider.acme.authorization-uri=https://sso.acme.example/authorize",
        "spring.security.oauth2.client.provider.acme.token-uri=https://sso.acme.example/token",
        "spring.security.oauth2.client.provider.acme.jwk-set-uri=https://sso.acme.example/jwks",
        "spring.security.oauth2.client.provider.acme.user-name-attribute=sub",
        "app.local-accounts.enabled=false"
})
class LocalAccountsDisabledTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theSignInPageHasNoPasswordForm() throws Exception {
        String page = mvc.perform(get("/login")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).doesNotContain("name=\"password\"").doesNotContain("href=\"/register\"");
    }

    @Test
    void theAccountScreensCannotBeReached() throws Exception {
        for (String path : new String[]{"/register", "/password/forgot", "/password/reset?token=x",
                "/register/complete?token=x"}) {
            mvc.perform(get(path)).andExpect(redirectedUrl("/login"));
        }
    }
}
