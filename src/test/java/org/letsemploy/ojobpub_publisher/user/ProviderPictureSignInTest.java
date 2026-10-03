package org.letsemploy.ojobpub_publisher.user;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The provider's picture through real sign-ins (spec 7.27): the address in the
 * {@code picture} claim is reported once per session, not on every request, so
 * a new picture at the provider is picked up at the next sign-in.
 *
 * <p>Not {@code @Transactional}, and without the dev bypass, which never reaches
 * the sign-in path. The accounts are removed after each test.
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
        "spring.security.oauth2.client.provider.oidc.authorization-uri=" + ProviderPictureSignInTest.IDP + "/auth",
        "spring.security.oauth2.client.provider.oidc.token-uri=" + ProviderPictureSignInTest.IDP + "/token",
        "spring.security.oauth2.client.provider.oidc.jwk-set-uri=" + ProviderPictureSignInTest.IDP + "/jwks",
        "spring.security.oauth2.client.provider.oidc.user-name-attribute=sub"})
class ProviderPictureSignInTest {

    static final String IDP = "https://idp.example";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private ClientRegistrationRepository registrations;

    /** Stands in for the service: what matters here is when it is told. */
    @MockitoBean
    private PictureService pictures;

    @AfterEach
    void removeTheAccountsTheseTestsCreated() {
        userRepo.findAll().stream().filter(u -> IDP.equals(u.getIssuer())).forEach(userRepo::delete);
    }

    private OidcLoginRequestPostProcessor as(String subject, String picture) {
        return oidcLogin()
                .clientRegistration(registrations.findByRegistrationId("oidc"))
                .idToken(t -> {
                    t.issuer(IDP).subject(subject).claim("email", subject + "@example.com")
                            .claim("email_verified", true);
                    if (picture != null) {
                        t.claim("picture", picture);
                    }
                });
    }

    private UUID idOf(String subject) {
        return userRepo.findByIssuerAndSubject(IDP, subject).orElseThrow().getId();
    }

    @Test
    void reportedOncePerSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/settings").session(session).with(as("pia", "https://cdn.example/pia.jpg")))
                    .andExpect(status().isOk());
        }
        verify(pictures, times(1)).providerPictureSeen(idOf("pia"), "https://cdn.example/pia.jpg");

        // The next sign-in is a new session, and asks again.
        mvc.perform(get("/settings").session(new MockHttpSession()).with(as("pia", "https://cdn.example/pia.jpg")))
                .andExpect(status().isOk());
        verify(pictures, times(2)).providerPictureSeen(idOf("pia"), "https://cdn.example/pia.jpg");
    }

    @Test
    void aChangedAddressIsReported_andSoIsNoneAtAll() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/settings").session(session).with(as("max", "https://cdn.example/1.jpg")));
        mvc.perform(get("/settings").session(session).with(as("max", "https://cdn.example/2.jpg")));
        mvc.perform(get("/settings").session(session).with(as("max", null)));
        mvc.perform(get("/settings").session(session).with(as("max", null)));

        UUID max = idOf("max");
        verify(pictures).providerPictureSeen(max, "https://cdn.example/1.jpg");
        verify(pictures).providerPictureSeen(max, "https://cdn.example/2.jpg");
        verify(pictures, times(1)).providerPictureSeen(eq(max), eq(null));
    }
}
