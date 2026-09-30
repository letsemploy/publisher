package org.letsemploy.ojobpub_publisher.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * mCaptcha, configured as a deployment would (spec 9.5): its vendored glue, the
 * instance's widget, and a policy that frames that instance on the captcha pages
 * alone (spec 7.2). Only rendering is tested; nothing calls the instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.WithoutDev.class)
@TestPropertySource(properties = {
        "app.local-accounts.enabled=true",
        "captcha.enabled=true",
        "captcha.provider=mcaptcha",
        "captcha.mcaptcha.url=https://mcaptcha.example.org",
        "captcha.mcaptcha.site-key=site-key-for-tests",
        "captcha.mcaptcha.secret=secret"
})
class MCaptchaTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theFormCarriesTheWidgetAndTheVendoredGlue() throws Exception {
        String page = mvc.perform(get("/password/forgot")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("data-mcaptcha_url=\"https://mcaptcha.example.org/widget/?sitekey=site-key-for-tests\"")
                .contains("id=\"mcaptcha__widget-container\"")
                .contains("src=\"/vendor/mcaptcha-glue.js\"")
                .doesNotContain("unpkg.com");
    }

    @Test
    void thePolicyFramesTheInstanceOnTheCaptchaPagesAlone() throws Exception {
        String policy = mvc.perform(get("/register")).andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(policy).contains("frame-src https://mcaptcha.example.org").contains("script-src 'self'")
                .doesNotContain("script-src 'self' https");
        String strict = mvc.perform(get("/login")).andReturn().getResponse().getHeader("Content-Security-Policy");
        assertThat(strict).doesNotContain("frame-src");
    }
}
