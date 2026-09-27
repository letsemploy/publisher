package org.letsemploy.ojobpub_publisher.security;

import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

/**
 * Loads a GitHub user (spec 2.2): the profile from {@code /user}, and the verified
 * address from {@code /user/emails}.
 *
 * <p>Spring calls this only for registrations without OpenID Connect, and the
 * startup check ({@link LoginOptions#requireSupportedProviders}) lets exactly one
 * such provider through: GitHub, with the {@code user:email} scope this needs.
 *
 * <p>{@code /user} carries only the <em>public</em> email, if the person chose to
 * show one, and says nothing about verification. Invitations go to verified
 * addresses only (spec 2.6), so the address comes from {@code /user/emails} - the
 * entry GitHub marks primary and verified - or not at all.
 *
 * <p>Not a bean: the OIDC chain constructs it, as it does its filters.
 */
@Slf4j
public class GitHubUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService profiles = new DefaultOAuth2UserService();
    private RestOperations rest = new RestTemplate();

    /** For tests: one client for both calls, so a mock server can answer them. */
    void setRestOperations(RestOperations rest) {
        this.rest = rest;
        this.profiles.setRestOperations(rest);
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User profile = profiles.loadUser(request);
        ClientRegistration registration = request.getClientRegistration();
        ClientRegistration.ProviderDetails provider = registration.getProviderDetails();
        return new GitHubUser(profile.getAuthorities(), profile.getAttributes(),
                provider.getUserInfoEndpoint().getUserNameAttributeName(),
                origin(provider.getAuthorizationUri()),
                verifiedPrimaryEmail(provider.getUserInfoEndpoint().getUri() + "/emails",
                        request.getAccessToken().getTokenValue(), profile.getName()));
    }

    /**
     * The primary address, if GitHub has verified it. A failure here does not fail
     * the sign-in: the identity is already certain, and without an address the
     * person can still sign in and create an employer - only not be invited.
     */
    private String verifiedPrimaryEmail(String uri, String token, String subject) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            List<Map<String, Object>> emails = rest.exchange(
                    new RequestEntity<>(headers, HttpMethod.GET, URI.create(uri)),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() { }).getBody();
            if (emails == null) {
                return null;
            }
            return emails.stream()
                    .filter(e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
                    .map(e -> (String) e.get("email"))
                    .findFirst()
                    .orElse(null);
        } catch (RestClientException e) {
            log.warn("Could not read the verified email of GitHub user {}; signing in without one: {}",
                    subject, e.getMessage());
            return null;
        }
    }

    /** {@code https://github.com/login/oauth/authorize} → {@code https://github.com}. */
    static String origin(String uri) {
        URI parsed = URI.create(uri);
        return parsed.getScheme() + "://" + parsed.getAuthority();
    }
}
