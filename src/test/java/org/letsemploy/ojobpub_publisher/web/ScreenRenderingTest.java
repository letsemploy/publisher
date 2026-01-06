package org.letsemploy.ojobpub_publisher.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Renders every screen of the real application against the seed data and asserts
 * the rules of specification section 7 that are checkable in markup.
 *
 * <p>These run through the production controllers, so a change to a view model or a
 * template cannot pass here while breaking the running application.
 *
 * <p>The dev profile supplies the authentication bypass (spec 2.3); the test profile
 * follows it so its datasource wins over the dev one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class ScreenRenderingTest {

    /** Fixed ids from data.sql. */
    private static final String EMPLOYER = "003d6aec-021b-11f1-aefa-f649a5d91690";
    private static final String JOB_PUBLISHED = "8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f";
    private static final String JOB_DRAFT = "bc47178c-f11d-490d-ce72-6d4e5f607182";
    private static final String JOB_INCOMPLETE = "de69390e-133f-4b2f-e094-8f6071829301";
    private static final String FEED_ALL = "cd58289d-022e-4a1e-df83-7e5f60718293";

    @Autowired
    private MockMvc mvc;

    static Stream<String> everyScreen() {
        return Stream.of(
                "/",
                "/jobs", "/jobs?q=zzzz-no-such-job", "/jobs?size=2", "/jobs?size=2&page=1",
                "/jobs?status=draft", "/jobs?jobType=permanent",
                "/jobs/create", "/jobs/" + JOB_PUBLISHED, "/jobs/" + JOB_DRAFT,
                "/jobs/" + JOB_INCOMPLETE, "/jobs/" + JOB_PUBLISHED + "/update",
                "/jobs/" + JOB_PUBLISHED + "/delete",
                "/jobs/tags/search?q=ja", "/jobs/locations/search?q=ber",
                "/feeds", "/feeds/create", "/feeds/" + FEED_ALL,
                "/feeds/" + FEED_ALL + "/update", "/feeds/" + FEED_ALL + "/delete",
                "/feeds/" + FEED_ALL + "/candidates",
                "/employers", "/employers/create", "/employers/" + EMPLOYER,
                "/employers/" + EMPLOYER + "/update",
                "/locations", "/locations/create",
                "/tags", "/tags/create", "/tags?q=jav");
    }

    private String html(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @ParameterizedTest(name = "renders {0}")
    @MethodSource("everyScreen")
    void screenRenders(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    /** An unresolved key must never reach the page (spec 8.1). */
    @ParameterizedTest(name = "no unresolved message key on {0}")
    @MethodSource("everyScreen")
    void noUnresolvedMessageKeys(String path) throws Exception {
        assertThat(html(path)).doesNotContainPattern("\\?\\?[\\w.]+\\?\\?");
    }

    /** Assets are self-hosted so the strict CSP of spec 9.4 can hold (spec 7.2). */
    @ParameterizedTest(name = "no external asset or inline script on {0}")
    @MethodSource("everyScreen")
    void javascriptBudgetHolds(String path) throws Exception {
        assertThat(html(path))
                .doesNotContain("cdn.", "unpkg.com", "jsdelivr", "googleapis")
                .doesNotContain("<script>");
    }

    @Test
    void shellIsProgressivelyEnhancedAndAccessible() throws Exception {
        assertThat(html("/jobs"))
                .contains("hx-boost=\"true\"")
                .contains("skip-link")
                .contains("aria-current=\"page\"")
                .contains("aria-live=\"polite\"")
                .contains("<noscript>")
                .contains("action=\"/jobs\"");
    }

    /** Every status in spec 7.6 is reachable from the seed data. */
    @Test
    void everyStatusBadgeIsExercised() throws Exception {
        String jobs = html("/jobs?size=100");
        assertThat(jobs).contains(">Published<", ">Expired<", ">Incomplete<", ">Draft<", ">Inactive<");
    }

    /** A job with no location can never be published, and the panel says which rule fails. */
    @Test
    void readinessPanelExplainsWhyAJobIsNotPublished() throws Exception {
        assertThat(html("/jobs/" + JOB_INCOMPLETE))
                .contains("requirement(s) still unmet")
                .contains("At least one location");
    }

    /** An empty result renders the empty state with the action that resolves it (spec 7.6). */
    @Test
    void emptyStateIsReachableWithoutFixtures() throws Exception {
        assertThat(html("/jobs?q=zzzz-no-such-job"))
                .contains("class=\"empty\"")
                .contains("No jobs yet");
    }

    /**
     * A validation error re-renders the form with every entered value preserved
     * (spec 7.7). This goes through the real validation, not a hand-made error map.
     */
    @Test
    void validationErrorsPreserveInput() throws Exception {
        String body = mvc.perform(post("/jobs/create")
                        .param("title", "")
                        .param("url", "not-a-url")
                        .param("language", "en")
                        .param("jobType", "permanent")
                        .param("category", "Engineering"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body)
                .contains("is-invalid")
                .contains("aria-invalid=\"true\"")
                .contains("Must be an absolute http(s) URL.")
                .contains("value=\"not-a-url\"")
                .contains("value=\"Engineering\"");
    }

    /** The feed screen shows the canonical URL of spec 5.1 and explains omissions. */
    @Test
    void feedScreenShowsCanonicalUrlAndExplainsOmissions() throws Exception {
        String body = html("/feeds/" + FEED_ALL);
        assertThat(body)
                .contains("acme-ag_" + EMPLOYER)
                .contains("/ojobpub.json")
                .contains("Omitted from the published document")
                .contains("Schema valid");
    }

    @Test
    void germanBundleRenders() throws Exception {
        assertThat(html("/jobs?lang=de")).contains("Stellen", "Entwurf");
    }

    @Test
    void pagingIsBookmarkableAndClamped() throws Exception {
        assertThat(html("/jobs?size=2")).contains("class=\"page-link\" href");
        // An out-of-range page clamps to the last available page rather than erroring (spec 8.3).
        mvc.perform(get("/jobs?size=2&page=99")).andExpect(status().isOk());
    }
}
