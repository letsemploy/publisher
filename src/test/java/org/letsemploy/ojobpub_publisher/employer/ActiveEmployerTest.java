package org.letsemploy.ojobpub_publisher.employer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
 * One employer is always active (spec 2.5): the first by name until the user
 * chooses, and the one just created or joined after that. There is no "all".
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
                .map(EmployerContext.class::cast)
                .findFirst()
                // Optional.map, not Stream.map: "no active employer" is null.
                .map(EmployerContext::getActiveEmployerId)
                .orElse(null);
    }

    /**
     * Switching lands on the dashboard (spec 7.3), not on the screen it came from,
     * which may show a record of the employer just left.
     */
    @Test
    void switchingGoesToTheDashboard() throws Exception {
        mvc.perform(post("/context/employer").session(new MockHttpSession())
                        .param("employerId", ACME)
                        .header("Referer", "http://localhost/jobs/" + UUID.randomUUID()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
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

    /** An employer that sorts before every other, so it is the first by name. */
    private String createFirst(MockHttpSession session) throws Exception {
        String location = mvc.perform(post("/employers/create").session(session)
                        .param("name", "0 First AG").param("hqCity", "Bern").param("hqCountry", "CH"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void withNothingChosenTheFirstByNameIsActive() throws Exception {
        String first = createFirst(new MockHttpSession());

        MockHttpSession fresh = new MockHttpSession();
        String dashboard = mvc.perform(get("/").session(fresh)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(activeEmployer(fresh)).as("resolved, and kept as the choice").isEqualTo(UUID.fromString(first));
        assertThat(mvc.perform(get("/locations/create").session(fresh))
                .andReturn().getResponse().getContentAsString()).contains("0 First AG");
        // The switcher, in the top bar (spec 7.3), offers the employers and nothing else.
        assertThat(dashboard.indexOf("action=\"/context/employer\""))
                .as("switcher after the sidebar").isGreaterThan(dashboard.indexOf("</aside>"));
        assertThat(dashboard)
                .containsPattern("dropdown-item active\"[^>]*>0 First AG<")
                .doesNotContain("All employers")
                .doesNotContain("name=\"employerId\" value=\"\"");
    }

    @Test
    void choosingAnotherSwitchesToIt() throws Exception {
        createFirst(new MockHttpSession());
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/context/employer").session(session).param("employerId", ACME));

        assertThat(activeEmployer(session)).isEqualTo(UUID.fromString(ACME));
        // The switcher marks it; every other employer is only a choice in the menu.
        assertThat(mvc.perform(get("/").session(session))
                .andReturn().getResponse().getContentAsString())
                .containsPattern("dropdown-item active\"[^>]*>Acme AG<")
                .doesNotContainPattern("dropdown-item active\"[^>]*>0 First AG<");
    }

    /** A choice the user can no longer see - left, removed, deleted - falls back to the first. */
    @Test
    void aChoiceNoLongerVisibleFallsBackToTheFirst() throws Exception {
        String first = createFirst(new MockHttpSession());
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/context/employer").session(session).param("employerId", UUID.randomUUID().toString()));

        mvc.perform(get("/").session(session)).andExpect(status().isOk());

        assertThat(activeEmployer(session)).isEqualTo(UUID.fromString(first));
    }

    /** There is no "none" to choose: a blank choice leaves the active employer as it was. */
    @Test
    void aBlankChoiceChangesNothing() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/context/employer").session(session).param("employerId", ACME));
        mvc.perform(post("/context/employer").session(session).param("employerId", ""));

        assertThat(activeEmployer(session)).isEqualTo(UUID.fromString(ACME));
    }
}
