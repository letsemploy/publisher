package org.letsemploy.ojobpub_publisher.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Values the screens never offer - a typed URL, a forged form - must not answer
 * 500 (spec 8.2). A list filter ignores them, a form field reports them, and a
 * status that does not exist is as unknown as a job that does not.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class JobInputTest {

    /** Fixed id from the seed: Acme's Senior Backend Engineer. */
    private static final String JOB = "8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f";

    @Autowired
    private MockMvc mvc;

    @Test
    void anUnknownJobTypeFilterIsIgnored() throws Exception {
        assertThat(mvc.perform(get("/jobs").param("jobType", "nonsense"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .contains("Senior Backend Engineer");
    }

    @Test
    void aForgedChoiceIsAFieldError() throws Exception {
        String body = mvc.perform(post("/jobs/create")
                        .param("title", "Forged")
                        .param("url", "https://www.acme.example/jobs/forged")
                        .param("language", "en")
                        .param("jobType", "permanent")
                        .param("workType", "levitating"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body)
                .contains("is-invalid")
                .contains("Choose one of the listed values.")
                .contains("value=\"Forged\"");
    }

    /** Two letters are not enough: the code must be one ISO 639-1 assigns (spec 3.3). */
    @Test
    void anUnassignedLanguageIsAFieldError() throws Exception {
        String body = mvc.perform(post("/jobs/create")
                        .param("title", "Forged")
                        .param("url", "https://www.acme.example/jobs/forged")
                        .param("language", "zz")
                        .param("jobType", "full_time"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body)
                .contains("is-invalid")
                .contains("Choose one of the listed values.")
                .contains("value=\"Forged\"");
    }

    /** The form offers every language, named in the reader's own. */
    @Test
    void theFormOffersEveryLanguage() throws Exception {
        String form = mvc.perform(get("/jobs/create").param("lang", "de"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(form)
                .contains("<option value=\"de\">Deutsch</option>")
                .contains("<option value=\"zu\">Zulu</option>")
                .contains("<option value=\"he\">Hebräisch</option>")
                .doesNotContain("<option value=\"iw\"");
    }

    @Test
    void anUnknownTargetStatusIsNotFound() throws Exception {
        mvc.perform(post("/jobs/" + JOB + "/status").param("to", "nonsense"))
                .andExpect(status().isNotFound());
    }
}
