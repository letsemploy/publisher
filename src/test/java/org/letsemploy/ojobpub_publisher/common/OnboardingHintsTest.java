package org.letsemploy.ojobpub_publisher.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dashboard's getting-started hints (spec 7.10): shown while they apply, and
 * never in admin mode. Sessions start in the user view here, so the seeded admin
 * sees what any member would.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@TestPropertySource(properties = "app.admin.start-in-admin-mode=false")
@Transactional
class OnboardingHintsTest {

    @Autowired
    private MockMvc mvc;

    private String dashboard(MockHttpSession session) throws Exception {
        return mvc.perform(get("/").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String createEmptyEmployer(MockHttpSession session) throws Exception {
        String location = mvc.perform(post("/employers/create").session(session)
                        .param("name", "Empty Start AG").param("hqCity", "Bern").param("hqCountry", "CH"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void anEmployerWithJobsNeedsNoHint() throws Exception {
        assertThat(dashboard(new MockHttpSession()))
                .doesNotContain("id=\"hint-employer\"")
                .doesNotContain("id=\"hint-job\"");
    }

    @Test
    void anEmployerWithoutJobsIsShownHowToCreateOne() throws Exception {
        MockHttpSession session = new MockHttpSession();
        createEmptyEmployer(session);

        assertThat(dashboard(session))
                .contains("id=\"hint-job\"")
                .contains("href=\"/jobs/create\"")
                .contains("No job yet")
                .doesNotContain("id=\"hint-employer\"");
    }

    /** Admin mode is the staff's view, not a newcomer's (spec 2.10). */
    @Test
    void adminModeShowsNoHint() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String empty = createEmptyEmployer(session);
        mvc.perform(post("/admin-mode/enter").session(session)).andExpect(status().is3xxRedirection());
        // Back to the employer without jobs: entering admin mode resets the choice.
        mvc.perform(post("/context/employer").session(session).param("employerId", empty));

        assertThat(dashboard(session)).doesNotContain("id=\"hint-job\"").doesNotContain("id=\"hint-employer\"");
    }
}
