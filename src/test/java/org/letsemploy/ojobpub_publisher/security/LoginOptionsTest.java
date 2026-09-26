package org.letsemploy.ojobpub_publisher.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * Which providers may sign people in, and how they are recognised (spec 2.2,
 * 7.19). Plain unit test: no Spring context, no database.
 */
class LoginOptionsTest {

    private static ClientRegistration oidc(String id, String authorizationUri) {
        return ClientRegistration.withRegistrationId(id)
                .clientId(id).clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(authorizationUri)
                .tokenUri("https://idp.example/token")
                .jwkSetUri("https://idp.example/jwks")
                .userNameAttributeName("sub")
                .build();
    }

    /**
     * GitHub is OAuth 2.0 without OpenID Connect. Its login would complete at
     * GitHub and then be nobody here, so it is refused at startup, saying why.
     */
    @Test
    void gitHubIsRefusedAndTheReasonNamed() {
        ClientRegistration github = CommonOAuth2Provider.GITHUB.getBuilder("github")
                .clientId("id").clientSecret("secret").build();
        assertThatThrownBy(() -> LoginOptions.requireOpenIdConnect(
                new InMemoryClientRegistrationRepository(oidc("google", "https://accounts.google.com/auth"), github)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'github'")
                .hasMessageContaining("OpenID Connect")
                .hasMessageContaining("no ID token");
    }

    @Test
    void openIdConnectProvidersPass() {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId("id").clientSecret("secret").build();
        assertThatCode(() -> LoginOptions.requireOpenIdConnect(new InMemoryClientRegistrationRepository(
                google, oidc("keycloak", "https://sso.example.com/realms/x/auth"))))
                .doesNotThrowAnyException();
    }

    /** By the authorisation host, never by the registration id the operator chose. */
    @Test
    void brandsAreRecognisedByTheirHost() {
        assertThat(LoginOptions.brandOf(oidc("anything", "https://accounts.google.com/o/oauth2/v2/auth")).key())
                .isEqualTo("google");
        assertThat(LoginOptions.brandOf(oidc("x", "https://gitlab.com/oauth/authorize")).key())
                .isEqualTo("gitlab");
        assertThat(LoginOptions.brandOf(oidc("x", "https://login.microsoftonline.com/t/oauth2/v2.0/authorize")).key())
                .isEqualTo("microsoft");
        assertThat(LoginOptions.brandOf(oidc("x", "https://acme.eu.auth0.com/authorize")).key())
                .isEqualTo("auth0");
        // A registration merely *called* google is not Google.
        assertThat(LoginOptions.brandOf(oidc("google", "https://sso.example.com/realms/x/auth"))).isNull();
    }
}
