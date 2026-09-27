package org.letsemploy.ojobpub_publisher.employer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.web.EmployerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating an employer makes it the active one (spec 2.5): what comes next -
 * its locations, jobs and feeds - belongs to it, not to whichever employer was
 * active before.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class ActiveEmployerTest {

    /** Acme, from the seed. */
    private static final String ACME = "003d6aec-021b-11f1-aefa-f649a5d91690";

    @Autowired
    private MockMvc mvc;

    /** The session-scoped context, as the session holds it. */
    private static UUID activeEmployer(MockHttpSession session) {
        return Collections.list(session.getAttributeNames()).stream()
                .map(session::getAttribute)
                .filter(EmployerContext.class::isInstance)
                .map(context -> ((EmployerContext) context).getActiveEmployerId())
                .findFirst().orElse(null);
    }

    @Test
    void aNewEmployerBecomesTheActiveOne() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/context/employer").session(session).param("employerId", ACME));
        assertThat(activeEmployer(session)).isEqualTo(UUID.fromString(ACME));

        String location = mvc.perform(post("/employers/create").session(session)
                        .param("name", "Freshly Made AG").param("hqCity", "Basel").param("hqCountry", "CH"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        UUID created = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        assertThat(activeEmployer(session)).isEqualTo(created);
        // And the next create form says whose row it will be.
        assertThat(mvc.perform(get("/locations/create").session(session))
                .andReturn().getResponse().getContentAsString()).contains("Freshly Made AG");
    }

    /**
     * Editing is not starting anew: the active employer stays as it was - here the
     * one just created, not the one being edited.
     */
    @Test
    void editingAnEmployerLeavesTheScopeAlone() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String location = mvc.perform(post("/employers/create").session(session)
                        .param("name", "Working On This AG").param("hqCity", "Bern").param("hqCountry", "CH"))
                .andReturn().getResponse().getRedirectedUrl();
        UUID workingOn = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        mvc.perform(post("/employers/" + ACME + "/update").session(session)
                        .param("name", "Acme AG").param("headquarters", "2c2e59d5-0b1a-11f1-938c-42a3421a666f"))
                .andExpect(status().is3xxRedirection());

        assertThat(activeEmployer(session)).isEqualTo(workingOn);
    }
}
