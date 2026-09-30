package org.letsemploy.ojobpub_publisher.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Local accounts end to end (spec 2.12, 7.24): signing up, the mailed links,
 * signing in, and what a password change does to other sessions - through the
 * real chain, without the development bypass and without any identity provider.
 *
 * <p>Not {@code @Transactional}: mail goes out after commit, which a test
 * transaction would never reach. Each request comes from its own client address,
 * so the per-client throttles of one test never reach another.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.WithoutDev.class)
@TestPropertySource(properties = {
        "app.local-accounts.enabled=true",
        "app.local-accounts.max-attempts=3",
        "app.local-accounts.max-mails=3"
})
class LocalAccountTest {

    private static final String PASSWORD = "correct horse battery";
    private static final Pattern LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final AtomicInteger CLIENTS = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private LocalAccountRepo accounts;
    @Autowired
    private AccountTokenRepo tokens;
    @Autowired
    private UserRepo users;
    @MockitoBean
    private JavaMailSender mailSender;

    @AfterEach
    void cleanUp() {
        users.findAll().stream().filter(u -> LocalAccount.ISSUER.equals(u.getIssuer())).forEach(users::delete);
        accounts.deleteAll();
    }

    @Test
    void signingUpConfirmsTheAddressAndTheLinkSetsThePassword() throws Exception {
        String email = address();
        signUp(email, "Ada");
        String token = lastToken(email);

        String form = mvc.perform(get("/register/complete").param("token", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(form).contains("name=\"token\"").contains(token);

        mvc.perform(from(post("/register/complete").param("token", token)
                        .param("password", PASSWORD).param("passwordRepeat", PASSWORD)))
                .andExpect(redirectedUrl("/login?verified"));

        MockHttpSession session = signIn(email, PASSWORD);
        mvc.perform(get("/").session(session)).andExpect(status().isOk());

        UserEntity user = users.findAll().stream().filter(u -> email.equals(u.getEmail())).findFirst().orElseThrow();
        assertThat(user.getIssuer()).isEqualTo(LocalAccount.ISSUER);
        assertThat(user.getSubject()).isEqualTo(accounts.findByEmailIgnoreCase(email).orElseThrow().getId().toString());
    }

    /** A pending sign-up has no password, so nobody can sign in to it - not even with a guess. */
    @Test
    void aPendingAccountCannotSignIn() throws Exception {
        String email = address();
        signUp(email, "Ada");
        mvc.perform(from(post("/login").param("email", email).param("password", PASSWORD)))
                .andExpect(redirectedUrl("/login?error"));
    }

    /** The screens are no oracle for which addresses exist (spec 2.6, 2.12). */
    @Test
    void signUpAndForgotAnswerAlikeForUnknownPendingAndTakenAddresses() throws Exception {
        String taken = verifiedAccount("Taken");
        String pending = address();
        signUp(pending, "Pending");
        String unknown = address();
        clearInvocations(mailSender);

        for (String email : List.of(taken, pending, unknown)) {
            mvc.perform(from(post("/register").param("email", email).param("name", "Someone")))
                    .andExpect(redirectedUrl("/register/sent"));
            mvc.perform(from(post("/password/forgot").param("email", email)))
                    .andExpect(redirectedUrl("/password/sent"));
        }
        List<SimpleMailMessage> sent = mails();
        // The taken address is told someone tried, and gets a reset link; the pending
        // one gets its sign-up link twice; the unknown one gets its first sign-up link.
        assertThat(sent).extracting(m -> m.getTo()[0]).containsOnly(taken, pending, unknown);
        assertThat(accounts.findByEmailIgnoreCase(taken).orElseThrow().getDisplayName()).isEqualTo("Taken");
    }

    @Test
    void aLinkWorksOnceAndNotAfterItExpires() throws Exception {
        String email = address();
        signUp(email, "Ada");
        String token = lastToken(email);
        complete(token, PASSWORD);

        String again = mvc.perform(get("/register/complete").param("token", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(again).contains("This link can no longer be used");

        mvc.perform(from(post("/password/forgot").param("email", email)));
        String reset = lastToken(email);
        AccountToken stored = tokens.findByTokenHash(LocalAccountService.sha256(reset)).orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        tokens.save(stored);
        assertThat(mvc.perform(get("/password/reset").param("token", reset))
                .andReturn().getResponse().getContentAsString()).contains("This link can no longer be used");
    }

    @Test
    void aNewLinkVoidsTheEarlierOne() throws Exception {
        String email = address();
        signUp(email, "Ada");
        String first = lastToken(email);
        mvc.perform(from(post("/register/resend").param("email", email)))
                .andExpect(redirectedUrl("/register/sent"));
        String second = lastToken(email);

        assertThat(second).isNotEqualTo(first);
        assertThat(mvc.perform(get("/register/complete").param("token", first))
                .andReturn().getResponse().getContentAsString()).contains("This link can no longer be used");
        complete(second, PASSWORD);
    }

    @Test
    void aWrongPasswordAnUnknownAddressAndAThrottledAttemptLookTheSame() throws Exception {
        String email = verifiedAccount("Ada");
        String client = client();
        mvc.perform(from(post("/login").param("email", address()).param("password", PASSWORD), client))
                .andExpect(redirectedUrl("/login?error"));
        for (int i = 0; i < 3; i++) {
            mvc.perform(from(post("/login").param("email", email).param("password", "wrong " + i), client))
                    .andExpect(redirectedUrl("/login?error"));
        }
        // Three failures for this address: now even the right password is refused, alike.
        mvc.perform(from(post("/login").param("email", email).param("password", PASSWORD), client()))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void changingThePasswordEndsEveryOtherSessionButThisOne() throws Exception {
        String email = verifiedAccount("Ada");
        MockHttpSession here = signIn(email, PASSWORD);
        MockHttpSession elsewhere = signIn(email, PASSWORD);

        String newPassword = "another long passphrase";
        mvc.perform(from(post("/account/password").session(here).param("current", PASSWORD)
                        .param("password", newPassword).param("passwordRepeat", newPassword)))
                .andExpect(redirectedUrl("/account/password"));

        mvc.perform(get("/").session(here)).andExpect(status().isOk());
        mvc.perform(get("/").session(elsewhere)).andExpect(redirectedUrl("/login?expired"));
        signIn(email, newPassword);
    }

    @Test
    void aResetEndsTheSessionsThatWereOpen() throws Exception {
        String email = verifiedAccount("Ada");
        MockHttpSession open = signIn(email, PASSWORD);

        mvc.perform(from(post("/password/forgot").param("email", email)));
        String newPassword = "a different passphrase";
        mvc.perform(from(post("/password/reset").param("token", lastToken(email))
                        .param("password", newPassword).param("passwordRepeat", newPassword)))
                .andExpect(redirectedUrl("/login?reset"));

        mvc.perform(get("/").session(open)).andExpect(redirectedUrl("/login?expired"));
        signIn(email, newPassword);
    }

    @Test
    void thePasswordRuleIsSaidAndKept() throws Exception {
        String email = address();
        signUp(email, "Ada");
        String token = lastToken(email);

        assertThat(choose(token, "too short", "too short")).contains("Between 12 and 128 characters.");
        assertThat(choose(token, email, email)).contains("must not be your email address");
        assertThat(choose(token, PASSWORD, PASSWORD + "!")).contains("are not the same");
        // A refused form keeps the link usable.
        complete(token, PASSWORD);
    }

    @Test
    void theChangeScreenIsOnlyForALocalAccountAndNeedsTheCurrentPassword() throws Exception {
        String email = verifiedAccount("Ada");
        MockHttpSession session = signIn(email, PASSWORD);
        assertThat(mvc.perform(get("/").session(session)).andReturn().getResponse().getContentAsString())
                .contains("href=\"/account/password\"");
        String refused = mvc.perform(from(post("/account/password").session(session)
                        .param("current", "not it at all").param("password", "another long passphrase")
                        .param("passwordRepeat", "another long passphrase")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(refused).contains("This is not your current password.");
    }

    @Test
    void aSuspendedLocalAccountIsSignedOut() throws Exception {
        String email = verifiedAccount("Ada");
        MockHttpSession session = signIn(email, PASSWORD);
        mvc.perform(get("/").session(session)).andExpect(status().isOk());
        UserEntity user = users.findAll().stream().filter(u -> email.equals(u.getEmail())).findFirst().orElseThrow();
        user.setSuspendedAt(Instant.now());
        users.save(user);
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/login?suspended"));
    }

    @Test
    void theFormsThatSendMailAreThrottledPerClient() throws Exception {
        String client = client();
        for (int i = 0; i < 3; i++) {
            mvc.perform(from(post("/password/forgot").param("email", address()), client))
                    .andExpect(redirectedUrl("/password/sent"));
        }
        String refused = mvc.perform(from(post("/password/forgot").param("email", address()), client))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(refused).contains("Too many requests from here");
    }

    @Test
    void theSignInPageOffersTheFormAndNoProviderButtons() throws Exception {
        String page = mvc.perform(get("/login")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("name=\"email\"").contains("name=\"password\"")
                .contains("href=\"/register\"").contains("href=\"/password/forgot\"")
                .doesNotContain("/oauth2/authorization/");
    }

    // ------------------------------------------------------------------ helpers

    private String verifiedAccount(String name) throws Exception {
        String email = address();
        signUp(email, name);
        complete(lastToken(email), PASSWORD);
        return email;
    }

    private void signUp(String email, String name) throws Exception {
        mvc.perform(from(post("/register").param("email", email).param("name", name)))
                .andExpect(redirectedUrl("/register/sent"));
    }

    private void complete(String token, String password) throws Exception {
        mvc.perform(from(post("/register/complete").param("token", token)
                        .param("password", password).param("passwordRepeat", password)))
                .andExpect(redirectedUrl("/login?verified"));
    }

    private String choose(String token, String password, String repeat) throws Exception {
        return mvc.perform(from(post("/register/complete").param("token", token)
                        .param("password", password).param("passwordRepeat", repeat)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        MvcResult result = mvc.perform(from(post("/login").param("email", email).param("password", password)))
                .andExpect(redirectedUrl("/")).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** The token of the last link mailed to this address. */
    private String lastToken(String email) {
        List<SimpleMailMessage> sent = mails().stream().filter(m -> email.equals(m.getTo()[0])).toList();
        assertThat(sent).as("mail to " + email).isNotEmpty();
        Matcher m = LINK.matcher(sent.getLast().getText());
        assertThat(m.find()).as("a link in the mail").isTrue();
        return m.group(1);
    }

    private List<SimpleMailMessage> mails() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeast(0)).send(captor.capture());
        return captor.getAllValues();
    }

    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request) {
        return from(request, client());
    }

    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request, String client) {
        return request.with(csrf()).with(r -> {
            r.setRemoteAddr(client);
            return r;
        });
    }

    private static String client() {
        int n = CLIENTS.getAndIncrement();
        return "10.9." + (n / 250) + "." + (n % 250 + 1);
    }

    private static String address() {
        return "local-" + UUID.randomUUID() + "@example.com";
    }
}
