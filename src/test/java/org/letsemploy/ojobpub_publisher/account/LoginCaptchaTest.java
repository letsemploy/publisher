package org.letsemploy.ojobpub_publisher.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * After repeated failed sign-ins, signing in also takes a solved captcha (spec
 * 2.12) - counted per address and per client, decided before the password is
 * looked at, and alike for addresses that exist and those that do not.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.WithoutDev.class)
@Import(HCaptchaTest.StandIn.class)
@TestPropertySource(properties = {
        "app.local-accounts.enabled=true",
        "app.local-accounts.captcha-after-failures=2",
        "app.local-accounts.max-attempts=10"
})
class LoginCaptchaTest {

    private static final String PASSWORD = "marmot violin tuesday gravel";
    private static final AtomicInteger CLIENTS = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private LocalAccountRepo accounts;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String email;

    @BeforeEach
    void anAccount() {
        LocalAccount account = new LocalAccount();
        email = "captcha-" + UUID.randomUUID() + "@example.com";
        account.setEmail(email);
        account.setDisplayName("Ada");
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account.setVerifiedAt(java.time.Instant.now());
        account.setPasswordChangedAt(java.time.Instant.now().minusSeconds(60));
        accounts.save(account);
    }

    @AfterEach
    void cleanUp() {
        accounts.deleteAll();
    }

    @Test
    void theSignInPageShowsTheCaptchaToAClientThatKeepsFailing() throws Exception {
        String client = client();
        assertThat(page(client)).doesNotContain("h-captcha");
        fail(email, client);
        fail(email, client);
        assertThat(page(client)).contains("class=\"h-captcha\"");
        assertThat(page(client())).as("another client").doesNotContain("h-captcha");
    }

    @Test
    void theRightPasswordNeedsTheCaptchaToo() throws Exception {
        String client = client();
        fail(email, client);
        fail(email, client);
        mvc.perform(login(email, PASSWORD, client, null)).andExpect(redirectedUrl("/login?captcha"));
        assertThat(mvc.perform(get("/login?captcha")).andReturn().getResponse().getContentAsString())
                .contains("please also solve the captcha").contains("class=\"h-captcha\"");
        mvc.perform(login(email, PASSWORD, client, "solved")).andExpect(redirectedUrl("/"));
    }

    /** Guessing one account's password from many clients still meets the captcha. */
    @Test
    void failuresForAnAddressRequireTheCaptchaFromAnyClient() throws Exception {
        fail(email, client());
        fail(email, client());
        mvc.perform(login(email, PASSWORD, client(), null)).andExpect(redirectedUrl("/login?captcha"));
    }

    /** The requirement follows the failures, not the account, so it discloses nothing (spec 2.6). */
    @Test
    void anUnknownAddressMeetsTheCaptchaExactlyLikeAKnownOne() throws Exception {
        String unknown = "nobody-" + UUID.randomUUID() + "@example.com";
        fail(unknown, client());
        fail(unknown, client());
        mvc.perform(login(unknown, PASSWORD, client(), null)).andExpect(redirectedUrl("/login?captcha"));
        mvc.perform(login(unknown, PASSWORD, client(), "solved")).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void aSuccessClearsTheAddressFailures() throws Exception {
        String client = client();
        fail(email, client);
        mvc.perform(login(email, PASSWORD, client, null)).andExpect(redirectedUrl("/"));
        fail(email, client());
        mvc.perform(login(email, PASSWORD, client(), null)).andExpect(redirectedUrl("/"));
    }

    private void fail(String address, String client) throws Exception {
        mvc.perform(login(address, "not the password", client, "solved")).andExpect(redirectedUrl("/login?error"));
    }

    private String page(String client) throws Exception {
        return mvc.perform(get("/login").with(r -> {
            r.setRemoteAddr(client);
            return r;
        })).andReturn().getResponse().getContentAsString();
    }

    private static MockHttpServletRequestBuilder login(String address, String password, String client,
                                                       String captchaToken) {
        MockHttpServletRequestBuilder request = post("/login").with(csrf())
                .param("email", address).param("password", password)
                .with(r -> {
                    r.setRemoteAddr(client);
                    return r;
                });
        return captchaToken == null ? request : request.param("h-captcha-response", captchaToken);
    }

    private static String client() {
        int n = CLIENTS.getAndIncrement();
        return "10.8." + (n / 250) + "." + (n % 250 + 1);
    }
}
