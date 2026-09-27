package org.letsemploy.ojobpub_publisher.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.security.AdminPolicy.AdminProperties;
import org.letsemploy.ojobpub_publisher.security.AdminPolicy.Group;
import org.letsemploy.ojobpub_publisher.security.AdminPolicy.Person;

/**
 * Who the configuration makes an admin (spec 2.1, 2.2). Plain unit test: no
 * Spring, no database.
 */
class AdminPolicyTest {

    private static final String GOOGLE = "https://accounts.google.com";
    private static final String GITHUB = "https://github.com";
    private static final String SSO = "https://sso.example.com/realms/acme";

    private static AdminPolicy policy(List<Person> people, List<Group> groups) {
        return new AdminPolicy(new AdminProperties(people, groups));
    }

    private static final AdminPolicy ALICE_AT_GOOGLE = policy(
            List.of(new Person(GOOGLE, "alice@example.com", null)), List.of());

    // --------------------------------------------------------------- people

    @Test
    void aVerifiedAddressFromThatProviderMatchesIgnoringCase() {
        assertThat(ALICE_AT_GOOGLE.isAdmin(GOOGLE, "1", "ALICE@example.com", true, Map.of()))
                .contains(true);
    }

    /**
     * An unverified address is whatever someone typed into their profile. This
     * holds even where require-verified-email is relaxed for invitations: that
     * setting decides what is stored, never who is admin.
     */
    @Test
    void anUnverifiedAddressNeverGrantsAdmin() {
        assertThat(ALICE_AT_GOOGLE.isAdmin(GOOGLE, "1", "alice@example.com", false, Map.of()))
                .contains(false);
    }

    /** The whole point of the scope: the same address verified elsewhere is not Alice. */
    @Test
    void theSameAddressFromAnotherProviderDoesNotMatch() {
        assertThat(ALICE_AT_GOOGLE.isAdmin(SSO, "1", "alice@example.com", true, Map.of()))
                .contains(false);
    }

    @Test
    void aSubjectMatchesWhateverTheAddress() {
        AdminPolicy octocat = policy(List.of(new Person(GITHUB, null, "583231")), List.of());
        assertThat(octocat.isAdmin(GITHUB, "583231", null, false, Map.of())).contains(true);
        assertThat(octocat.isAdmin(GITHUB, "583232", null, false, Map.of())).contains(false);
    }

    @Test
    void aTrailingSlashOnTheIssuerDoesNotMatter() {
        AdminPolicy slashed = policy(List.of(new Person(GOOGLE + "/", "alice@example.com", null)), List.of());
        assertThat(slashed.isAdmin(GOOGLE, "1", "alice@example.com", true, Map.of())).contains(true);
    }

    // --------------------------------------------------------------- groups

    private static final AdminPolicy SSO_GROUP = policy(List.of(),
            List.of(new Group(SSO, "groups", "ojobpub-admin")));

    @Test
    void aGroupClaimMatchesAsAListOrASingleValue() {
        assertThat(SSO_GROUP.isAdmin(SSO, "1", null, false,
                Map.of("groups", List.of("staff", "ojobpub-admin")))).contains(true);
        assertThat(SSO_GROUP.isAdmin(SSO, "1", null, false,
                Map.of("groups", "ojobpub-admin"))).contains(true);
        assertThat(SSO_GROUP.isAdmin(SSO, "1", null, false,
                Map.of("groups", List.of("staff")))).contains(false);
        assertThat(SSO_GROUP.isAdmin(SSO, "1", null, false, Map.of())).contains(false);
    }

    /** Keycloak puts realm roles in realm_access.roles, a claim within a claim. */
    @Test
    void aDottedPathReachesANestedClaim() {
        AdminPolicy realmRole = policy(List.of(),
                List.of(new Group(SSO, "realm_access.roles", "admin")));
        assertThat(realmRole.isAdmin(SSO, "1", null, false,
                Map.of("realm_access", Map.of("roles", List.of("admin", "user"))))).contains(true);
        assertThat(realmRole.isAdmin(SSO, "1", null, false,
                Map.of("realm_access", "not-a-map"))).contains(false);
    }

    /** A provider we do not run cannot grant admin by sending the right claim. */
    @Test
    void anotherProvidersIdenticalClaimDoesNotMatch() {
        assertThat(SSO_GROUP.isAdmin("https://evil.example", "1", null, false,
                Map.of("groups", List.of("ojobpub-admin")))).contains(false);
    }

    // ------------------------------------------------------------ unmanaged

    /** Nothing configured: the stored role stands, so upgrading demotes nobody. */
    @Test
    void withNothingConfiguredTheRoleIsLeftAlone() {
        AdminPolicy none = policy(null, null);
        assertThat(none.managed()).isFalse();
        assertThat(none.isAdmin(GOOGLE, "1", "alice@example.com", true, Map.of())).isEmpty();
    }

    // ----------------------------------------------------------- validation

    @Test
    void aPersonNeedsExactlyOneOfEmailAndSubject() {
        assertThatThrownBy(() -> policy(List.of(new Person(GOOGLE, "a@example.com", "1")), List.of()))
                .hasMessageContaining("app.admin.people[0]").hasMessageContaining("exactly one");
        assertThatThrownBy(() -> policy(List.of(new Person(GOOGLE, null, null)), List.of()))
                .hasMessageContaining("exactly one");
    }

    @Test
    void everyRuleNeedsItsIssuer() {
        assertThatThrownBy(() -> policy(List.of(new Person(null, "a@example.com", null)), List.of()))
                .hasMessageContaining("needs an issuer");
        assertThatThrownBy(() -> policy(List.of(), List.of(new Group(SSO, "groups", " "))))
                .hasMessageContaining("app.admin.groups[0]");
    }
}
