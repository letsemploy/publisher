package org.letsemploy.ojobpub_publisher.security;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.stereotype.Component;

/**
 * The identity providers the sign-in page offers, one button each (spec 7.19).
 *
 * <p>Read from the configured registrations, so adding a provider is
 * configuration only: its {@code spring.security.oauth2.client.registration.*}
 * properties, and nothing here or in a template.
 *
 * <p>Sorted by label. The configured order is not available to sort by: Boot
 * binds the registrations into a hash map, so they arrive in an order that
 * means nothing and may change when one is added.
 */
@Component
public class LoginOptions {

    /** One button: where it leads, what it says, which logo it carries. */
    public record LoginOption(String href, String label, String brand) {
    }

    /**
     * Recognised by the host of the authorisation endpoint, which every
     * registration has - not by the registration id, which is whatever the
     * operator chose. A host not listed here gets the generic icon.
     */
    private static final Map<String, Brand> BRANDS = Map.of(
            "accounts.google.com", new Brand("google", "Google"),
            "gitlab.com", new Brand("gitlab", "GitLab"),
            "login.microsoftonline.com", new Brand("microsoft", "Microsoft"),
            "appleid.apple.com", new Brand("apple", "Apple"));

    record Brand(String key, String name) {
    }

    private final ObjectProvider<ClientRegistrationRepository> registrations;

    public LoginOptions(ObjectProvider<ClientRegistrationRepository> registrations) {
        this.registrations = registrations;
    }

    public List<LoginOption> all() {
        List<LoginOption> options = new ArrayList<>();
        for (ClientRegistration registration : registrations(registrations.getIfAvailable())) {
            Brand brand = brandOf(registration);
            options.add(new LoginOption(
                    "/oauth2/authorization/" + registration.getRegistrationId(),
                    label(registration, brand),
                    brand == null ? null : brand.key()));
        }
        // Named ones alphabetically; an unnamed "Sign in" last.
        options.sort(Comparator.comparing(LoginOption::label,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return options;
    }

    /**
     * Only OpenID Connect providers can sign someone in here (spec 2.2): a person is
     * the issuer and subject of an ID token, and invitations need a verified email.
     * A registration that does not ask for {@code openid} would complete its login
     * at the provider and then be nobody here, so it is refused at startup instead.
     */
    public static void requireOpenIdConnect(ClientRegistrationRepository repository) {
        for (ClientRegistration registration : registrations(repository)) {
            if (!registration.getScopes().contains(OidcScopes.OPENID)) {
                String id = registration.getRegistrationId();
                String why = registration.getProviderDetails().getAuthorizationUri().contains("github.com")
                        ? " GitHub offers OAuth 2.0 but not OpenID Connect: it has no ID token or"
                                + " verified email to identify a person by."
                        : " Add the openid scope if the provider supports OpenID Connect.";
                throw new IllegalStateException("Sign-in provider '" + id + "' does not request the"
                        + " openid scope. Only OpenID Connect providers are supported (spec 2.2)."
                        + why);
            }
        }
    }

    /** Boot's repository is iterable; anything else offers nothing to list. */
    @SuppressWarnings("unchecked")
    private static Iterable<ClientRegistration> registrations(ClientRegistrationRepository repository) {
        return repository instanceof Iterable<?> iterable
                ? (Iterable<ClientRegistration>) iterable
                : List.of();
    }

    static Brand brandOf(ClientRegistration registration) {
        String host = URI.create(registration.getProviderDetails().getAuthorizationUri()).getHost();
        if (host == null) {
            return null;
        }
        if (host.endsWith(".auth0.com")) {
            return new Brand("auth0", "Auth0");
        }
        return BRANDS.get(host);
    }

    /**
     * The configured {@code client-name}. Spring defaults it to the registration
     * id, which is configuration and not a name, so then the brand's own name is
     * used - or none, and the page says a plain "Sign in".
     */
    private static String label(ClientRegistration registration, Brand brand) {
        String name = registration.getClientName();
        if (name != null && !name.isBlank() && !name.equals(registration.getRegistrationId())) {
            return name;
        }
        return brand == null ? null : brand.name();
    }
}
