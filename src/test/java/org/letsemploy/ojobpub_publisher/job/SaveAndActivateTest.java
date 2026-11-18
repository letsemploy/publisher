package org.letsemploy.ojobpub_publisher.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Save and activate (spec 7.11): when activation is refused, the draft is still
 * saved and its page says what is missing, rather than the whole save being lost.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class SaveAndActivateTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JobRepo jobRepo;

    @Test
    void aRefusedActivationKeepsTheDraft() throws Exception {
        String title = "No location " + UUID.randomUUID();

        mvc.perform(post("/jobs/create")
                        .param("title", title)
                        .param("url", "https://example.com/jobs/1")
                        .param("language", "en")
                        .param("jobType", "permanent")
                        .param("action", "activate"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMsg", "job.readiness.blocked"));

        assertThat(jobRepo.findAll()).filteredOn(j -> title.equals(j.getTitle()))
                .singleElement().extracting(Job::getStatus).isEqualTo(JobStatus.DRAFT);
    }
}
