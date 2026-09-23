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
    private static final String MEMBER = "44444444-4444-4444-8444-444444444444";
    private static final String TOKEN = "77777777-7777-4777-8777-777777777777";

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
                "/tags", "/tags/create", "/tags?q=jav",
                "/invitations", "/people",
                "/employers/" + EMPLOYER + "/people",
                "/employers/" + EMPLOYER + "/people/members/" + MEMBER + "/remove",
                "/employers/" + EMPLOYER + "/tokens",
                "/employers/" + EMPLOYER + "/tokens/" + TOKEN + "/revoke");
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

    /** Our own module is served; hyperscript is gone and nothing evaluates strings (spec 7.2). */
    @Test
    void behaviourComesFromOurOwnModuleOnly() throws Exception {
        String html = html("/jobs");
        assertThat(html).contains("/js/app.js").contains("/vendor/htmx.min.js");
        assertThat(html).doesNotContain("hyperscript");
    }

    /**
     * A control that only works with JavaScript is hidden until the module reveals
     * it, so no screen offers a dead button (spec 7.1).
     */
    @Test
    void javascriptOnlyControlsAreHiddenUntilEnhanced() throws Exception {
        // The copy control lives on the feed detail screen, beside the public URL.
        String feed = html("/feeds/" + FEED_ALL);
        assertThat(feed).contains("data-action=\"copy\"").contains("data-enhanced").contains("hidden");
        // Chip removal on the job form is the other enhancement-only control.
        assertThat(html("/jobs/" + JOB_PUBLISHED + "/update"))
                .contains("data-action=\"remove-chip\"").contains("data-enhanced");
    }

    /** Nothing evaluates code from a string any more, so the CSP needs no unsafe-eval. */
    @Test
    void noTemplateEvaluatesCodeFromAString() throws Exception {
        for (String path : new String[]{"/feeds/" + FEED_ALL, "/jobs/" + JOB_PUBLISHED + "/update",
                "/jobs/" + JOB_PUBLISHED + "/delete"}) {
            assertThat(html(path)).as(path).doesNotContain("_=\"on ").doesNotContain("hyperscript");
        }
    }

    /**
     * Identity and employer context live in the top bar; the sidebar is navigation
     * only (spec 7.3). Asserted by position, because both are dropdowns and a
     * class check alone would not notice one drifting back into the sidebar.
     */
    @Test
    void userMenuAndEmployerSwitcherLiveInTheTopBar() throws Exception {
        String html = html("/jobs");
        int sidebarEnd = html.indexOf("</aside>");
        int userMenu = html.indexOf("/logout");
        int employerSwitcher = html.indexOf("/context/employer");
        assertThat(sidebarEnd).as("sidebar present").isPositive();
        assertThat(userMenu).as("user menu after the sidebar").isGreaterThan(sidebarEnd);
        assertThat(employerSwitcher).as("employer switcher after the sidebar").isGreaterThan(sidebarEnd);
        assertThat(html).contains("/context/theme");
        // The sidebar still carries the destinations and the invitation badge.
        String sidebar = html.substring(0, sidebarEnd);
        assertThat(sidebar).contains("nav-link-title").contains("/invitations");
    }

    /**
     * Theme and language must not be boosted. hx-boost swaps the body only, so the
     * `data-bs-theme` and `lang` attributes on <html> would keep their old values
     * and the change would not appear until the next full page load.
     */
    @Test
    void themeAndLanguageForceAFullNavigation() throws Exception {
        String html = html("/jobs");
        int themeForm = html.indexOf("/context/theme");
        assertThat(themeForm).isPositive();
        assertThat(html.substring(themeForm - 120, themeForm + 120))
                .as("theme form opts out of boosting").contains("hx-boost=\"false\"");
        int langLink = html.indexOf("lang=de");
        assertThat(langLink).isPositive();
        assertThat(html.substring(langLink - 200, langLink + 120))
                .as("language links opt out of boosting").contains("hx-boost=\"false\"");
    }

    /** The attribute the theme actually depends on is on <html>, and reflects the session. */
    @Test
    void themeIsRenderedOnTheHtmlElement() throws Exception {
        assertThat(html("/jobs")).containsPattern("<html[^>]*data-bs-theme=\"(light|dark)\"");
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

    /**
     * The other half of {@code PeopleScreenTest}, which renders this screen as an
     * editor: an owner must still get everything. The two together are what keep
     * the permission split honest in both directions.
     */
    @Test
    void anOwnerGetsTheInviteFormAndPendingInvitationsOnTheSidebarScreen() throws Exception {
        assertThat(html("/people"))
                .contains("Mara Member")
                .contains("Pending invitations")
                .contains("Send invitation")
                // Mara is an editor, so her control offers promotion; the only
                // owner is the last one, whose controls are refused (spec 2.7).
                .contains("Make owner")
                .contains("Promote someone else to owner first")
                .doesNotContain("ask one of the owners");
    }

    /** The seed data leaves an invitation pending, so the sidebar badge has a value. */
    @Test
    void pendingInvitationsAreSurfacedOnThePeopleScreen() throws Exception {
        assertThat(html("/employers/" + EMPLOYER + "/people"))
                .contains("Pending invitations")
                .contains("editor@example.com")
                .contains("Mara Member")
                .contains("Send invitation");
    }

    /**
     * The register is what makes a forgotten integration visible (spec 7.17), so
     * the list must name the token, its public prefix and its scopes - and must
     * never show a secret, which is only ever rendered once at creation.
     */
    @Test
    void tokenScreenListsWhatMakesADormantTokenNoticeable() throws Exception {
        String body = html("/employers/" + EMPLOYER + "/tokens");
        assertThat(body)
                .contains("ats-sync")
                .contains("ojp_seedonly01")
                .contains("jobs:write")
                .contains("Never used")
                .contains("Manage people lets whatever holds this token")
                .doesNotContain("secret_hash")
                .doesNotContain("$2a$");
    }

    /**
     * An empty state belongs to an empty list only (spec 7.6).
     *
     * <p>Regression guard for a Thymeleaf precedence trap: {@code th:replace}
     * (precedence 100) is processed before {@code th:if} (300), so an element
     * carrying both is replaced by the fragment before the condition is ever
     * evaluated - and the empty state renders next to a full table. Every list
     * screen was written that way. The condition now lives on a wrapping
     * {@code th:block}, which leaves no markup of its own.
     */
    @Test
    void anEmptyStateIsNotRenderedBesideAFullList() throws Exception {
        assertThat(html("/jobs"))
                .as("six seeded jobs, so no empty state")
                .contains("Senior Backend Engineer")
                .doesNotContain("No jobs yet");
        assertThat(html("/feeds")).doesNotContain("No feeds yet");
        assertThat(html("/employers/" + EMPLOYER + "/people"))
                .contains("Mara Member")
                .doesNotContain("No members yet");
    }

    /** The same trap, and the same guard, for the htmx fragment variant. */
    @Test
    void theJobFragmentDoesNotCarryAnEmptyState() throws Exception {
        String fragment = mvc.perform(get("/jobs").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(fragment).contains("Senior Backend Engineer").doesNotContain("No jobs yet");
    }

    /**
     * The readiness panel had it worst: both the satisfied and the unsatisfied
     * icon rendered on every row, so the panel said yes and no at once.
     */
    @Test
    void aReadinessRowShowsOneIconNotBoth() throws Exception {
        String body = html("/jobs/" + JOB_PUBLISHED);
        // Counted inside the readiness list only, so the dev banner's own
        // warning icon cannot be mistaken for a readiness row.
        int start = body.indexOf("list-unstyled mb-0");
        String panel = body.substring(start, body.indexOf("</ul>", start));
        assertThat(countOf(panel, "ti-circle-check"))
                .as("one check per satisfied requirement")
                .isEqualTo(6);
        assertThat(countOf(panel, "ti-alert-triangle"))
                .as("this job meets every requirement, so no warnings")
                .isZero();
    }

    private static int countOf(String haystack, String needle) {
        int n = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    /**
     * The same URL answers with a fragment when htmx asks for one and with a
     * whole page otherwise (spec 9.2). Routing between the two is done by
     * htmx-spring-boot's {@code @HxRequest(boosted = false)}, a third-party
     * annotation carrying a rule this application depends on, and nothing else
     * here exercises it - a library upgrade could change the matching and every
     * other test would still pass.
     */
    @Test
    void htmxAsksForAFragmentAndGetsOne() throws Exception {
        String fragment = mvc.perform(get("/jobs").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(fragment)
                .as("a fragment carries the table but not the page shell")
                .doesNotContain("<html")
                .doesNotContain("navbar")
                .contains("table-responsive")
                .contains("Senior Backend Engineer");
    }

    /**
     * A boosted navigation is an htmx request that still wants a whole page,
     * which is exactly what {@code boosted = false} exists to say. Get this
     * wrong and every link in the application returns a bare fragment.
     */
    @Test
    void aBoostedNavigationStillGetsTheWholePage() throws Exception {
        String page = mvc.perform(get("/jobs")
                        .header("HX-Request", "true")
                        .header("HX-Boosted", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("<html").contains("navbar");
    }

    /**
     * A missing record renders the error page. Regression guard: the error view
     * decorates with the shell, and @ModelAttribute does not reach @ExceptionHandler,
     * so the handler must supply `ui` itself or every 404 becomes a 500.
     */
    @Test
    void notFoundRendersTheErrorPage() throws Exception {
        mvc.perform(get("/jobs/11111111-2222-3333-4444-555555555555"))
                .andExpect(status().isNotFound());
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
