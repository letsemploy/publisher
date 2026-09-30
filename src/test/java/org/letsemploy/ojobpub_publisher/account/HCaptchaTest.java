package org.letsemploy.ojobpub_publisher.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mosersystems.captcha.CaptchaProvider;
import com.mosersystems.captcha.CaptchaVerificationException;
import com.mosersystems.captcha.CaptchaVerifier;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * hCaptcha on the forms that send mail (spec 2.12): the widget, a refusal without
 * a solved captcha, and a content security policy widened for hCaptcha on those
 * pages alone (spec 7.2). The verifier is a stand-in; nothing leaves the machine.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.WithoutDev.class)
@TestPropertySource(properties = "app.local-accounts.enabled=true")
class HCaptchaTest {

    /** Solves only the token "solved", as the provider would a real one. */
    @TestConfiguration
    static class StandIn {
        @Bean
        CaptchaVerifier captchaVerifier() {
            return new CaptchaVerifier() {
                public CaptchaProvider provider() {
                    return CaptchaProvider.HCAPTCHA;
                }

                public String siteKey() {
                    return "site-key-for-tests";
                }

                public String tokenParameterName() {
                    return "h-captcha-response";
                }

                public void verify(String token, String remoteIp) {
                    if (!"solved".equals(token)) {
                        throw new CaptchaVerificationException("not solved");
                    }
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private LocalAccountRepo accounts;
    @MockitoBean
    private JavaMailSender mailSender;

    @AfterEach
    void cleanUp() {
        accounts.deleteAll();
    }

    @Test
    void theSignUpFormCarriesTheWidget() throws Exception {
        String page = mvc.perform(get("/register")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("class=\"h-captcha\"").contains("data-sitekey=\"site-key-for-tests\"")
                .contains("src=\"https://js.hcaptcha.com/1/api.js\"");
    }

    @Test
    void anUnsolvedCaptchaIsRefusedAndMailsNobody() throws Exception {
        String email = "captcha-" + UUID.randomUUID() + "@example.com";
        String refused = mvc.perform(post("/register").with(csrf())
                        .param("email", email).param("name", "Bot"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(refused).contains("Please confirm that you are not a robot.");
        assertThat(accounts.findByEmailIgnoreCase(email)).isEmpty();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));

        mvc.perform(post("/register").with(csrf())
                        .param("email", email).param("name", "Person").param("h-captcha-response", "solved"))
                .andExpect(redirectedUrl("/register/sent"));
        assertThat(accounts.findByEmailIgnoreCase(email)).isPresent();
    }

    @Test
    void thePolicyIsWidenedOnTheCaptchaPagesAlone() throws Exception {
        for (String path : new String[]{"/register", "/register/resend", "/password/forgot"}) {
            String policy = mvc.perform(get(path)).andReturn().getResponse().getHeader("Content-Security-Policy");
            assertThat(policy).as(path).contains("script-src 'self' https://hcaptcha.com https://*.hcaptcha.com")
                    .contains("frame-src https://hcaptcha.com").contains("frame-ancestors 'none'")
                    .doesNotContain("unsafe-inline").doesNotContain("unsafe-eval");
        }
        String strict = mvc.perform(get("/login")).andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(strict).contains("script-src 'self'").doesNotContain("hcaptcha");
    }
}
