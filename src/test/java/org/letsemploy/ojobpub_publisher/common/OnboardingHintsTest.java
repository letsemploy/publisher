package org.letsemploy.ojobpub_publisher.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.feed.Permalink;
import org.letsemploy.ojobpub_publisher.feed.PermalinkCheck;
import org.letsemploy.ojobpub_publisher.feed.PermalinkRepo;
import org.letsemploy.ojobpub_publisher.web.EmployerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dashboard's getting-started hints (spec 7.10), and the domain asked for on
 * the first dashboard visit of a session (spec 7.23): shown while they apply, and
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
    @Autowired
    private PermalinkRepo permalinkRepo;

    private String dashboard(MockHttpSession session) throws Exception {
        return mvc.perform(get("/").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Creates an employer, which becomes the active one, and answers its id. */
    private String createEmptyEmployer(MockHttpSession session) throws Exception {
        mvc.perform(post("/employers/create").session(session)
                        .param("name", "Empty Start AG").param("hqCity", "Bern").param("hqCountry", "CH"))
                .andExpect(status().is3xxRedirection());
        return createdIn(session);
    }

    private Permalink defaultPermalink(String employer) {
        return permalinkRepo.findByEmployerIdOrderByNameAsc(UUID.fromString(employer)).getFirst();
    }

    /** The seed's employer has its domains, one of them checked: nothing is asked or hinted. */
    @Test
    void anEmployerOnItsWebsiteIsNotAskedForADomain() throws Exception {
        assertThat(dashboard(new MockHttpSession())).doesNotContain("id=\"hint-domain\"");
    }

    /**
     * A new employer goes straight to its default permalink's domain, and so does
     * the first dashboard visit of a session while it has none - once, so the
     * dashboard is the next page whatever the person does (spec 7.23).
     */
    @Test
    void theDomainIsAskedForOncePerSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String location = mvc.perform(post("/employers/create").session(session)
                        .param("name", "Empty Start AG").param("hqCity", "Bern").param("hqCountry", "CH"))
                .andReturn().getResponse().getRedirectedUrl();
        String employer = createdIn(session);
        Permalink permalink = defaultPermalink(employer);
        String setup = "/domains/" + permalink.getId() + "/update";
        assertThat(location).isEqualTo(setup);
        assertThat(mvc.perform(get(setup).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .contains("id=\"domain-asked\"", "Which domain are your jobs for?");

        mvc.perform(get("/").session(session)).andExpect(redirectedUrl(setup));
        // Asked once: the dashboard is next, and keeps saying it.
        assertThat(dashboard(session)).contains("id=\"hint-domain\"", "href=\"/domains\"");

        // A new session - a new sign-in - asks again.
        MockHttpSession next = new MockHttpSession();
        mvc.perform(post("/context/employer").session(next).param("employerId", employer));
        mvc.perform(get("/").session(next)).andExpect(redirectedUrl(setup));
    }

    /** The hint stays until a domain passes its check, served or redirected (spec 7.10). */
    @Test
    void theHintGoesOnceADomainWorks() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Permalink permalink = defaultPermalink(createEmptyEmployer(session));
        permalink.setWebsiteHost("acme.ch");
        permalinkRepo.saveAndFlush(permalink);
        // It has its domain now, so the dashboard is not replaced; not checked yet, so the hint stays.
        assertThat(dashboard(session)).contains("id=\"hint-domain\"");

        permalink.setCheckResult(PermalinkCheck.Result.REDIRECT);
        permalinkRepo.saveAndFlush(permalink);
        assertThat(dashboard(session)).doesNotContain("id=\"hint-domain\"");
    }

    /**
     * With jobs live and no domain that passed its check, the hint is a warning:
     * the jobs reach no one (spec 7.10). The seed's careers page passed; here it
     * has not.
     */
    @Test
    void liveJobsOnNoWebsiteAreAWarning() throws Exception {
        Permalink careers = permalinkRepo.findById(UUID.fromString("5a5a5a5a-5a5a-4a5a-8a5a-5a5a5a5a5a5a")).orElseThrow();
        careers.setCheckResult(PermalinkCheck.Result.NOT_FOUND);
        permalinkRepo.saveAndFlush(careers);

        String html = dashboard(new MockHttpSession());
        String hint = html.substring(html.indexOf("id=\"hint-domain\"") - 200, html.indexOf("id=\"hint-domain\"") + 600);
        assertThat(hint).contains("alert-warning", "Your jobs are live, but on no website").doesNotContain("alert-info");
    }

    /** A new employer has no live job yet: a note, like the other hints. */
    @Test
    void withoutLiveJobsItIsANote() throws Exception {
        MockHttpSession session = new MockHttpSession();
        createEmptyEmployer(session);
        mvc.perform(get("/").session(session)).andExpect(status().is3xxRedirection());

        String html = dashboard(session);
        String hint = html.substring(html.indexOf("id=\"hint-domain\"") - 200, html.indexOf("id=\"hint-domain\"") + 600);
        assertThat(hint).contains("alert-info", "Not on your website yet").doesNotContain("alert-warning");
    }

    private static String createdIn(MockHttpSession session) {
        return Collections.list(session.getAttributeNames()).stream()
                .map(session::getAttribute)
                .filter(EmployerContext.class::isInstance)
                .map(c -> ((EmployerContext) c).getActiveEmployerId().toString())
                .findFirst().orElseThrow();
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
        // The first visit asks for the domain instead (spec 7.23).
        mvc.perform(get("/").session(session)).andExpect(status().is3xxRedirection());

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

        // Nor is the domain asked for: the dashboard at once, without the hint.
        assertThat(dashboard(session)).doesNotContain("id=\"hint-job\"").doesNotContain("id=\"hint-employer\"")
                .doesNotContain("id=\"hint-domain\"");
    }
}
