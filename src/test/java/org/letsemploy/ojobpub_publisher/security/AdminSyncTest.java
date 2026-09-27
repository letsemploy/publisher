package org.letsemploy.ojobpub_publisher.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Admins from configuration, through real sign-ins (spec 2.1, 2.2): the list and
 * the group mapping promote, and anyone else holding the role is demoted.
 *
 * <p>What the role is for is checked too, not only the column: an admin sees
 * every employer, and anyone else gets 404 for one they do not belong to.
 *
 * <p>Not {@code @Transactional}: the role is written by one request and read by
 * the next, as in production. The accounts are removed after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.WithoutDev.class)
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.oidc.client-id=publisher",
        "spring.security.oauth2.client.registration.oidc.client-secret=secret",
        "spring.security.oauth2.client.registration.oidc.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.oidc.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.oidc.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.provider.oidc.authorization-uri=" + AdminSyncTest.IDP + "/auth",
        "spring.security.oauth2.client.provider.oidc.token-uri=" + AdminSyncTest.IDP + "/token",
        "spring.security.oauth2.client.provider.oidc.jwk-set-uri=" + AdminSyncTest.IDP + "/jwks",
        "spring.security.oauth2.client.provider.oidc.user-name-attribute=sub",
        "app.admin.people[0].issuer=" + AdminSyncTest.IDP,
        "app.admin.people[0].email=chief@example.com",
        "app.admin.groups[0].issuer=" + AdminSyncTest.IDP,
        "app.admin.groups[0].claim=groups",
        "app.admin.groups[0].value=ojobpub-admin"})
class AdminSyncTest {

    static final String IDP = "https://idp.example";

    /** Acme, from the seed. Nobody signing in here is a member of it. */
    private static final String ACME = "/employers/003d6aec-021b-11f1-aefa-f649a5d91690";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private ClientRegistrationRepository registrations;

    @AfterEach
    void removeTheAccountsTheseTestsCreated() {
        userRepo.findAll().stream().filter(u -> IDP.equals(u.getIssuer())).forEach(userRepo::delete);
    }

    private OidcLoginRequestPostProcessor as(String subject, String email, boolean verified,
                                             List<String> groups) {
        return oidcLogin()
                .clientRegistration(registrations.findByRegistrationId("oidc"))
                .idToken(t -> t.issuer(IDP).subject(subject)
                        .claim("email", email).claim("email_verified", verified)
                        .claim("groups", groups));
    }

    private UserEntity.Role roleOf(String subject) {
        return userRepo.findByIssuerAndSubject(IDP, subject).orElseThrow().getRole();
    }

    /** Listed by verified address: admin from the first request, and it shows. */
    @Test
    void aListedVerifiedAddressMakesAnAdmin() throws Exception {
        mvc.perform(get(ACME).with(as("chief", "chief@example.com", true, List.of())))
                .andExpect(status().isOk());
        assertThat(roleOf("chief")).isEqualTo(UserEntity.Role.ADMIN);
    }

    /** The same address, unverified, is anybody's - and gets nobody's employers. */
    @Test
    void theSameAddressUnverifiedDoesNot() throws Exception {
        mvc.perform(get(ACME).with(as("pretender", "chief@example.com", false, List.of())))
                .andExpect(status().isNotFound());
        assertThat(roleOf("pretender")).isEqualTo(UserEntity.Role.USER);
    }

    @Test
    void theMappedGroupMakesAnAdmin() throws Exception {
        mvc.perform(get(ACME).with(as("staff", "staff@example.com", true, List.of("ojobpub-admin"))))
                .andExpect(status().isOk());
        assertThat(roleOf("staff")).isEqualTo(UserEntity.Role.ADMIN);
    }

    /**
     * Authoritative: an admin the configuration no longer names is demoted on the
     * very next request - here, one made admin by hand in the database.
     */
    @Test
    void anAdminNoLongerNamedIsDemoted() throws Exception {
        UserEntity former = new UserEntity();
        former.setIssuer(IDP);
        former.setSubject("former");
        former.setEmail("former@example.com");
        former.setDisplayName("former");
        former.setRole(UserEntity.Role.ADMIN);
        userRepo.save(former);

        mvc.perform(get(ACME).with(as("former", "former@example.com", true, List.of())))
                .andExpect(status().isNotFound());
        assertThat(roleOf("former")).isEqualTo(UserEntity.Role.USER);
    }

    /** And leaving the group demotes too, at the next sign-in that carries the change. */
    @Test
    void leavingTheGroupDemotes() throws Exception {
        mvc.perform(get("/").with(as("mover", "mover@example.com", true, List.of("ojobpub-admin"))));
        assertThat(roleOf("mover")).isEqualTo(UserEntity.Role.ADMIN);

        mvc.perform(get("/").with(as("mover", "mover@example.com", true, List.of("staff"))));
        assertThat(roleOf("mover")).isEqualTo(UserEntity.Role.USER);
    }
}
