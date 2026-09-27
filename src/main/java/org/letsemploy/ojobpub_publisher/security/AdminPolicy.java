package org.letsemploy.ojobpub_publisher.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Who is a platform admin, as the operator configured it (spec 2.1, 2.2).
 *
 * <p>Two kinds of rule, both <strong>scoped to one provider by its issuer</strong>:
 * a list of people, matched by verified email or by subject, and group claims,
 * matched by a value in a claim the provider sends. The scope is the point. An
 * email is not an identity, and with several providers configured an unscoped
 * address would make admin whoever got it verified at any of them.
 *
 * <p><strong>Authoritative when configured.</strong> With any rule present, every
 * request re-decides the role: a person matched is admin, an admin not matched is
 * not. With no rule at all, {@link #isAdmin} answers empty and roles are left as
 * they are, so an installation with hand-made admins is not demoted by upgrading.
 */
@Component
@EnableConfigurationProperties(AdminPolicy.AdminProperties.class)
@Slf4j
public class AdminPolicy {

    /**
     * {@code app.admin.people} and {@code app.admin.groups}. A person names exactly
     * one of {@code email} and {@code subject}; a group names the claim - a dotted
     * path reaches a nested one, like {@code realm_access.roles} - and the value.
     */
    @ConfigurationProperties("app.admin")
    public record AdminProperties(List<Person> people, List<Group> groups) {
        public AdminProperties {
            people = people == null ? List.of() : List.copyOf(people);
            groups = groups == null ? List.of() : List.copyOf(groups);
        }
    }

    public record Person(String issuer, String email, String subject) {
    }

    public record Group(String issuer, String claim, String value) {
    }

    private final AdminProperties properties;

    public AdminPolicy(AdminProperties properties) {
        this.properties = properties;
        validate(properties);
        if (managed()) {
            log.info("Platform admins are managed by configuration: {} people, {} group rules."
                    + " Anyone else holding the admin role is demoted at their next request.",
                    properties.people().size(), properties.groups().size());
        }
    }

    public boolean managed() {
        return !properties.people().isEmpty() || !properties.groups().isEmpty();
    }

    /**
     * Whether this signed-in identity should be an admin, or empty when admins are
     * not managed here and the stored role stands.
     *
     * <p>An email grants admin only when {@code emailVerified} - independently of
     * {@code app.oidc.require-verified-email}. Relaxing that lets an unverified
     * address be kept for invitations; it must never let one grant admin, or anyone
     * could type the admin's address into their profile at the provider.
     */
    public Optional<Boolean> isAdmin(String issuer, String subject, String email,
                                     boolean emailVerified, Map<String, Object> claims) {
        if (!managed()) {
            return Optional.empty();
        }
        String from = normalise(issuer);
        boolean person = properties.people().stream()
                .filter(p -> normalise(p.issuer()).equals(from))
                .anyMatch(p -> p.subject() != null
                        ? p.subject().equals(subject)
                        : emailVerified && email != null && p.email().equalsIgnoreCase(email));
        boolean group = properties.groups().stream()
                .filter(g -> normalise(g.issuer()).equals(from))
                .anyMatch(g -> contains(claim(claims, g.claim()), g.value()));
        return Optional.of(person || group);
    }

    /** {@code realm_access.roles} walks into nested maps; a missing step is no claim. */
    static Object claim(Map<String, Object> claims, String path) {
        Object current = claims;
        for (String step : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(step);
        }
        return current;
    }

    /** A claim is a single value or a list of them; either way, does it hold this one? */
    private static boolean contains(Object claim, String value) {
        if (claim instanceof Collection<?> values) {
            return values.stream().anyMatch(v -> value.equals(String.valueOf(v)));
        }
        return claim != null && value.equals(String.valueOf(claim));
    }

    private static String normalise(String issuer) {
        if (issuer == null) {
            return "";
        }
        return issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
    }

    /**
     * A mistake here must stop the application, not pass silently: a rule that
     * matches nothing locks every admin out, and one that matches too much lets
     * someone in.
     */
    static void validate(AdminProperties properties) {
        for (int i = 0; i < properties.people().size(); i++) {
            Person p = properties.people().get(i);
            String at = "app.admin.people[" + i + "]";
            require(!blank(p.issuer()), at + " needs an issuer: which provider this person signs in with");
            require(blank(p.email()) != blank(p.subject()),
                    at + " needs exactly one of email or subject");
        }
        for (int i = 0; i < properties.groups().size(); i++) {
            Group g = properties.groups().get(i);
            String at = "app.admin.groups[" + i + "]";
            require(!blank(g.issuer()) && !blank(g.claim()) && !blank(g.value()),
                    at + " needs an issuer, a claim and a value");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Invalid admin configuration (spec 2.2): " + message);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
