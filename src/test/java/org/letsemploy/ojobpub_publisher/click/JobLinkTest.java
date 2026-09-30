package org.letsemploy.ojobpub_publisher.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A job's public link (spec 5.6): a published job's counts the click and
 * redirects, any other job's leads nowhere, and nothing about the visitor is kept.
 *
 * <p>Not {@code @Transactional}: the click is counted in a transaction of its own,
 * which a test transaction would not see roll back. Today's counters for the
 * seeded jobs are removed after each test instead; the seed's own are dated
 * earlier, so they are untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@TestPropertySource(properties = {
        "app.clicks.country-header=CF-IPCountry",
        // MaxMind's test database, where 81.2.69.160 is in GB.
        "app.clicks.geoip-database=src/test/resources/geoip/GeoIP2-Country-Test.mmdb"
})
class JobLinkTest {

    private static final String BROWSER = "Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0";
    private static final String ACME = "003d6aec-021b-11f1-aefa-f649a5d91690";
    private static final String FEED_ALL = "cd58289d-022e-4a1e-df83-7e5f60718293";
    private static final UUID PUBLISHED = UUID.fromString("8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f");
    private static final String PUBLISHED_URL = "https://www.acme.example/jobs/ACME-2026-014";
    private static final UUID EXPIRED = UUID.fromString("ab36067b-e00c-489c-bd61-5c3d4e5f6071");
    private static final UUID DRAFT = UUID.fromString("bc47178c-f11d-490d-ce72-6d4e5f607182");
    private static final UUID INACTIVE = UUID.fromString("cd58289d-022e-4a1e-df83-7e5f60718201");
    private static final UUID INCOMPLETE = UUID.fromString("de69390e-133f-4b2f-e094-8f6071829301");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JobClickRepo clicks;

    @AfterEach
    void removeTodaysClicks() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        clicks.deleteAll(clicks.findAll().stream().filter(c -> c.getId().day().equals(today)).toList());
    }

    private static MockHttpServletRequestBuilder follow(UUID job) {
        return get("/go/" + job).header("User-Agent", BROWSER);
    }

    private long today(UUID job, String country) {
        return clicks.findById(new JobClick.Key(job, LocalDate.now(ZoneOffset.UTC), country))
                .map(JobClick::getClicks).orElse(0L);
    }

    private long todayAnywhere() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return clicks.findAll().stream().filter(c -> c.getId().day().equals(today))
                .mapToLong(JobClick::getClicks).sum();
    }

    @Test
    void aPublishedJobsLinkRedirectsToItsPageAndCountsTheClick() throws Exception {
        MvcResult result = mvc.perform(follow(PUBLISHED))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(PUBLISHED_URL))
                // Fetched afresh every time, or a cached redirect swallows the click.
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Robots-Tag", "noindex"))
                .andReturn();
        assertThat(today(PUBLISHED, JobClick.UNKNOWN_COUNTRY)).isEqualTo(1);

        mvc.perform(follow(PUBLISHED)).andExpect(status().isFound());
        assertThat(today(PUBLISHED, JobClick.UNKNOWN_COUNTRY)).as("one counter, incremented").isEqualTo(2);

        // Public and stateless like the feed (spec 5.1): no session, no cookie.
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
    }

    @Test
    void theCountryComesFromTheHeaderThenTheDatabase() throws Exception {
        mvc.perform(follow(PUBLISHED).header("CF-IPCountry", "de")).andExpect(status().isFound());
        assertThat(today(PUBLISHED, "DE")).isEqualTo(1);

        // Cloudflare's "unknown" is no country; the database answers instead.
        mvc.perform(follow(PUBLISHED).header("CF-IPCountry", "XX").with(r -> {
            r.setRemoteAddr("81.2.69.160");
            return r;
        })).andExpect(status().isFound());
        assertThat(today(PUBLISHED, "GB")).isEqualTo(1);

        // Tor, and an address the database does not know: unknown.
        mvc.perform(follow(PUBLISHED).header("CF-IPCountry", "T1")).andExpect(status().isFound());
        assertThat(today(PUBLISHED, JobClick.UNKNOWN_COUNTRY)).isEqualTo(1);
    }

    @Test
    void checksAndMachinesAreNotCounted() throws Exception {
        mvc.perform(head("/go/" + PUBLISHED).header("User-Agent", BROWSER)).andExpect(status().isFound());
        mvc.perform(get("/go/" + PUBLISHED).header("User-Agent", "Slackbot-LinkExpanding 1.0"))
                .andExpect(status().isFound());
        mvc.perform(get("/go/" + PUBLISHED).header("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1)"))
                .andExpect(status().isFound());
        mvc.perform(get("/go/" + PUBLISHED)).andExpect(status().isFound());
        assertThat(todayAnywhere()).isZero();
    }

    /** The employer's control: once a job leaves the feed, no copy of the feed leads to it. */
    @Test
    void aJobThatIsNotPublishedIsGoneAndNotCounted() throws Exception {
        for (UUID job : List.of(EXPIRED, DRAFT, INACTIVE, INCOMPLETE)) {
            mvc.perform(follow(job))
                    .andExpect(status().isGone())
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "Acme AG is no longer advertising this position.")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "href=\"https://www.acme.example\"")));
        }
        assertThat(todayAnywhere()).isZero();
    }

    @Test
    void theGonePageSpeaksTheBrowsersLanguage() throws Exception {
        mvc.perform(follow(EXPIRED).header("Accept-Language", "de-CH,de;q=0.9,en;q=0.8"))
                .andExpect(status().isGone())
                .andExpect(header().string("Content-Language", "de"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Diese Stelle ist nicht mehr verfügbar")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("??"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "00000000-0000-4000-8000-000000000000"})
    void anUnknownLinkIsNotFoundAndNamesNoEmployer(String id) throws Exception {
        mvc.perform(get("/go/" + id).header("User-Agent", BROWSER))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Job not found")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Acme"))));
    }

    @Test
    void oneSpellingPerLink() throws Exception {
        mvc.perform(get("/go/" + PUBLISHED.toString().toUpperCase()).header("User-Agent", BROWSER))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/go/" + PUBLISHED));
        assertThat(todayAnywhere()).as("the canonical request counts, not the nudge").isZero();
    }

    /** The published document hands out the link, never the page itself (spec 6.4). */
    @Test
    void theFeedPublishesTheLink() throws Exception {
        mvc.perform(get("/ojobpub/v1/acme-ag_" + ACME + "/all_" + FEED_ALL + "/ojobpub.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[*].url", org.hamcrest.Matchers.hasItem(
                        "http://localhost:8080/go/" + PUBLISHED)))
                .andExpect(jsonPath("$.jobs[*].url", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(PUBLISHED_URL))));
    }
}
