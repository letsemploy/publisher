package org.letsemploy.ojobpub_publisher.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Who someone is when they sign in with GitHub (spec 2.2). Plain unit test: GitHub's
 * two endpoints are answered by a mock server, no Spring context, no database.
 */
class GitHubUserServiceTest {

    private static final String PROFILE = """
            {"id": 583231, "login": "octocat", "name": "The Octocat", "email": null}""";

    private final GitHubUserService service = new GitHubUserService();
    private MockRestServiceServer github;

    private final ClientRegistration registration = CommonOAuth2Provider.GITHUB.getBuilder("github")
            .clientId("client").clientSecret("secret")
            .scope("read:user", "user:email")
            .build();

    @BeforeEach
    void mockGitHub() {
        RestTemplate rest = new RestTemplate();
        github = MockRestServiceServer.bindTo(rest).build();
        service.setRestOperations(rest);
    }

    private GitHubUser signIn(String profile, String emails) {
        github.expect(requestTo("https://api.github.com/user"))
                .andRespond(withSuccess(profile, MediaType.APPLICATION_JSON));
        github.expect(requestTo("https://api.github.com/user/emails"))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(emails == null ? withServerError()
                        : withSuccess(emails, MediaType.APPLICATION_JSON));
        GitHubUser user = (GitHubUser) service.loadUser(new OAuth2UserRequest(registration,
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token",
                        Instant.now(), Instant.now().plusSeconds(3600))));
        github.verify();
        return user;
    }

    /**
     * Identified by the numeric id, which never changes - not by the login handle,
     * which its owner can rename - and by github.com as issuer.
     */
    @Test
    void theIdentityIsGitHubsNumericId() {
        GitHubUser user = signIn(PROFILE, "[]");
        assertThat(user.getIssuer()).isEqualTo("https://github.com");
        assertThat(user.getSubject()).isEqualTo("583231");
        assertThat(user.getDisplayName()).isEqualTo("The Octocat");
    }

    /** The address GitHub marks primary and verified, and no other (spec 2.6). */
    @Test
    void theVerifiedPrimaryAddressIsTaken() {
        GitHubUser user = signIn(PROFILE, """
                [{"email": "old@example.com", "primary": false, "verified": true},
                 {"email": "octo@example.com", "primary": true, "verified": true}]""");
        assertThat(user.getVerifiedEmail()).isEqualTo("octo@example.com");
    }

    /** A primary address GitHub has not verified could be anybody's. */
    @Test
    void anUnverifiedPrimaryAddressIsNotTaken() {
        GitHubUser user = signIn(PROFILE, """
                [{"email": "claimed@example.com", "primary": true, "verified": false},
                 {"email": "other@example.com", "primary": false, "verified": true}]""");
        assertThat(user.getVerifiedEmail()).isNull();
    }

    /**
     * The identity is certain without an address, so a failed call costs the
     * address - the person can sign in, just not be invited - not the sign-in.
     */
    @Test
    void aFailedEmailLookupSignsInWithoutAnAddress() {
        GitHubUser user = signIn(PROFILE, null);
        assertThat(user.getSubject()).isEqualTo("583231");
        assertThat(user.getVerifiedEmail()).isNull();
    }

    @Test
    void withoutAProfileNameTheLoginIsShown() {
        GitHubUser user = signIn("""
                {"id": 1, "login": "octocat", "name": null, "email": "public@example.com"}""", "[]");
        assertThat(user.getDisplayName()).isEqualTo("octocat");
        assertThat(user.getPublicEmail()).isEqualTo("public@example.com");
        assertThat(user.getVerifiedEmail()).isNull();
    }
}
