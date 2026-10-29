package org.letsemploy.ojobpub_publisher.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Two requests of one person's first sign-in at once (spec 2.2) - two tabs, or a
 * page and its assets right after the provider's callback. Both find no account
 * and both create one; the unique (issuer, subject) key refuses the second.
 *
 * <p>Made deterministic rather than raced: the winner's account is already
 * committed, and the lookup answers "none" for it until something inserts a new
 * account, which is exactly what the losing request sees. Without handling, its
 * insert failed at commit and the request answered 500.
 *
 * <p>Not {@code @Transactional}: the account must be committed, as the winner's
 * would be, and is removed after the test.
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
        "spring.security.oauth2.client.provider.oidc.authorization-uri=" + FirstSignInRaceTest.IDP + "/auth",
        "spring.security.oauth2.client.provider.oidc.token-uri=" + FirstSignInRaceTest.IDP + "/token",
        "spring.security.oauth2.client.provider.oidc.jwk-set-uri=" + FirstSignInRaceTest.IDP + "/jwks",
        "spring.security.oauth2.client.provider.oidc.user-name-attribute=sub"})
class FirstSignInRaceTest {

    static final String IDP = "https://race.idp.example";
    private static final String SUBJECT = "racer";

    @Autowired
    private MockMvc mvc;
    @MockitoSpyBean
    private UserRepo userRepo;
    @Autowired
    private ClientRegistrationRepository registrations;

    @AfterEach
    void removeTheAccount() {
        userRepo.findAll().stream().filter(u -> IDP.equals(u.getIssuer())).forEach(userRepo::delete);
    }

    /** Creates the account a concurrent request would have: committed, before ours inserts. */
    private void theWinnerSignsInFirst() {
        UserEntity winner = new UserEntity();
        winner.setIssuer(IDP);
        winner.setSubject(SUBJECT);
        winner.setEmail("racer@example.com");
        winner.setDisplayName("Racer");
        winner.setRole(UserEntity.Role.USER);
        userRepo.saveAndFlush(winner);
    }

    /** Our request: finds no account until it has tried to insert its own. */
    private void andOurRequestStillFindsNone() {
        AtomicBoolean inserted = new AtomicBoolean();
        doAnswer(inv -> SUBJECT.equals(inv.getArgument(1)) && !inserted.get()
                ? Optional.empty() : real(inv))
                .when(userRepo).findByIssuerAndSubject(any(), any());
        doAnswer(inv -> insert(inv, inserted)).when(userRepo).save(any(UserEntity.class));
        doAnswer(inv -> insert(inv, inserted)).when(userRepo).saveAndFlush(any(UserEntity.class));
    }

    private Object insert(InvocationOnMock inv, AtomicBoolean inserted) throws Throwable {
        if (inv.getArgument(0) instanceof UserEntity user && user.getId() == null
                && SUBJECT.equals(user.getSubject())) {
            inserted.set(true);
        }
        return real(inv);
    }

    /**
     * The repository's own answer. A repository is an interface proxy, which
     * {@code callRealMethod} cannot call through; Spring spies on one with a
     * default answer that delegates to it, so that answer is asked.
     */
    private Object real(InvocationOnMock inv) throws Throwable {
        return Mockito.mockingDetails(userRepo).getMockCreationSettings().getDefaultAnswer().answer(inv);
    }

    @Test
    void theLosingRequestSignsInWithTheWinnersAccount() throws Exception {
        theWinnerSignsInFirst();
        andOurRequestStillFindsNone();

        mvc.perform(get("/").with(oidcLogin()
                        .clientRegistration(registrations.findByRegistrationId("oidc"))
                        .idToken(t -> t.issuer(IDP).subject(SUBJECT)
                                .claim("email", "racer@example.com").claim("email_verified", true))))
                .andExpect(status().isOk());

        assertThat(userRepo.findAll().stream().filter(u -> IDP.equals(u.getIssuer())))
                .as("still one account").hasSize(1);
    }
}
