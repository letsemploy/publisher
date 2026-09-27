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
     * GitHub is the one provider accepted without OpenID Connect, but only with the
     * user:email scope: without it no verified address can be read, and nobody who
     * signs in through it could ever be invited (spec 2.2, 2.6). Spring's own
     * GitHub defaults ask for read:user alone, so this is the common mistake.
     */
    @Test
    void gitHubWithoutTheEmailScopeIsRefusedWithTheFix() {
        ClientRegistration github = CommonOAuth2Provider.GITHUB.getBuilder("github")
                .clientId("id").clientSecret("secret").build();
        assertThatThrownBy(() -> LoginOptions.requireSupportedProviders(
                new InMemoryClientRegistrationRepository(github)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'github'")
                .hasMessageContaining("user:email")
                .hasMessageContaining("scope: read:user,user:email");
    }

    @Test
    void gitHubWithTheEmailScopeIsAccepted() {
        ClientRegistration github = CommonOAuth2Provider.GITHUB.getBuilder("github")
                .clientId("id").clientSecret("secret").scope("read:user", "user:email").build();
        assertThatCode(() -> LoginOptions.requireSupportedProviders(
                new InMemoryClientRegistrationRepository(github)))
                .doesNotThrowAnyException();
    }

    /** Any other provider without OpenID Connect would sign people in as nobody. */
    @Test
    void otherOAuth2OnlyProvidersAreStillRefused() {
        ClientRegistration facebook = CommonOAuth2Provider.FACEBOOK.getBuilder("facebook")
                .clientId("id").clientSecret("secret").build();
        assertThatThrownBy(() -> LoginOptions.requireSupportedProviders(
                new InMemoryClientRegistrationRepository(facebook)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'facebook'")
                .hasMessageContaining("openid scope");
    }

    @Test
    void openIdConnectProvidersPass() {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId("id").clientSecret("secret").build();
        assertThatCode(() -> LoginOptions.requireSupportedProviders(new InMemoryClientRegistrationRepository(
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
        assertThat(LoginOptions.brandOf(oidc("x", "https://github.com/login/oauth/authorize")).key())
                .isEqualTo("github");
        assertThat(LoginOptions.brandOf(oidc("x", "https://acme.eu.auth0.com/authorize")).key())
                .isEqualTo("auth0");
        // A registration merely *called* google is not Google.
        assertThat(LoginOptions.brandOf(oidc("google", "https://sso.example.com/realms/x/auth"))).isNull();
    }
}
