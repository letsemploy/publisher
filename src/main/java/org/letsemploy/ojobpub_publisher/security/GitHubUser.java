package org.letsemploy.ojobpub_publisher.security;

import java.util.Collection;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

/**
 * Someone signed in through GitHub (spec 2.2) - the one provider accepted without
 * OpenID Connect, and so the one whose identity is assembled here rather than
 * read from an ID token.
 *
 * <p>The subject is GitHub's numeric user id ({@link #getName()}), which never
 * changes; the {@code login} handle can be renamed and is only ever a label. The
 * issuer is the origin of the registration's authorisation endpoint, so it is
 * fixed per installation. {@link #getVerifiedEmail()} is the address GitHub
 * reports as primary and verified, or null.
 *
 * <p>Kept in the session, hence only serialisable fields.
 */
public class GitHubUser extends DefaultOAuth2User {

    private final String issuer;
    private final String verifiedEmail;

    public GitHubUser(Collection<? extends GrantedAuthority> authorities, Map<String, Object> attributes,
                      String nameAttributeKey, String issuer, String verifiedEmail) {
        super(authorities, attributes, nameAttributeKey);
        this.issuer = issuer;
        this.verifiedEmail = verifiedEmail;
    }

    public String getIssuer() {
        return issuer;
    }

    /** GitHub's numeric id, as text: the stable half of the identity. */
    public String getSubject() {
        return getName();
    }

    public String getVerifiedEmail() {
        return verifiedEmail;
    }

    /** The public email of the profile, verified or not - used only when that is allowed. */
    public String getPublicEmail() {
        return text(getAttribute("email"));
    }

    /** The profile's name, falling back to the login handle. */
    public String getDisplayName() {
        String name = text(getAttribute("name"));
        return name != null ? name : text(getAttribute("login"));
    }

    /** The profile picture, copied into the application rather than linked (spec 7.27). */
    public String getAvatarUrl() {
        return text(getAttribute("avatar_url"));
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
