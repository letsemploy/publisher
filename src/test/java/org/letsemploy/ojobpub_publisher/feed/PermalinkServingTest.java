package org.letsemploy.ojobpub_publisher.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.dto.OjobpubDto;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.service.OjobpubValidator;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * The public permalink endpoint (spec 5.5): it serves whichever feed it points
 * at, or the employer with no jobs, and its date only ever moves forward so a
 * cache revalidating it sees a switch.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class PermalinkServingTest {

    /** Fixed ids from the seed. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID FEED_ALL = UUID.fromString("cd58289d-022e-4a1e-df83-7e5f60718293");
    private static final UUID FEED_ENGINEERING = UUID.fromString("de69390e-133f-4b2f-e094-8f6071829304");
    /** Publishes Engineering, which holds one publishable job. */
    private static final UUID CAREERS = UUID.fromString("5a5a5a5a-5a5a-4a5a-8a5a-5a5a5a5a5a5a");
    /** Publishes no feed. */
    private static final UUID PARTNER = UUID.fromString("5b5b5b5b-5b5b-4b5b-8b5b-5b5b5b5b5b5b");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PermalinkService permalinkService;
    @Autowired
    private FeedService feedService;
    @Autowired
    private OjobpubValidator validator;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EntityManager entityManager;

    private final Actor owner = Actor.user(UUID.randomUUID(), "Owner", "owner@example.com", false,
            Map.of(ACME, MembershipRole.OWNER));

    private static String url(UUID permalink) {
        return "/ojobpub/v1/permalink/" + permalink + "/ojobpub.json";
    }

    private MvcResult fetch(UUID permalink) throws Exception {
        return mvc.perform(get(url(permalink))).andExpect(status().isOk()).andReturn();
    }

    private OjobpubDto document(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), OjobpubDto.class);
    }

    /** What a later request in another transaction would see. */
    private void commitLikeARequest() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void servesTheFeedItPointsAt() throws Exception {
        MvcResult result = fetch(CAREERS);
        String feed = mvc.perform(get(feedPath(FEED_ENGINEERING)))
                .andReturn().getResponse().getContentAsString();
        // The same jobs and employer; only the date may be the permalink's own.
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).get("jobs"))
                .isEqualTo(objectMapper.readTree(feed).get("jobs"));
        assertThat(document(result).getJobs()).hasSize(1);
        mvc.perform(get(url(CAREERS)).header("Origin", "https://example.org"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Cache-Control", "max-age=300, public"));
    }

    /** The feed's own URL, found through the feed endpoint's redirect from a stale slug. */
    private String feedPath(UUID feed) throws Exception {
        String stale = "/ojobpub/v1/x_" + ACME + "/x_" + feed + "/ojobpub.json";
        return mvc.perform(get(stale)).andReturn().getResponse().getHeader("Location");
    }

    @Test
    void withNoFeedItPublishesTheEmployerWithNoJobs() throws Exception {
        MvcResult result = fetch(PARTNER);
        OjobpubDto document = document(result);
        assertThat(document.getJobs()).isEmpty();
        assertThat(document.getEmployer().getName()).isEqualTo("Acme AG");
        assertThat(validator.validate(document)).as("schema violations").isEmpty();
    }

    @Test
    void switchingChangesWhatTheSameUrlServes() throws Exception {
        permalinkService.retarget(CAREERS, FEED_ALL, owner);
        commitLikeARequest();
        assertThat(document(fetch(CAREERS)).getJobs()).hasSizeGreaterThan(1);

        permalinkService.retarget(CAREERS, null, owner);
        commitLikeARequest();
        assertThat(document(fetch(CAREERS)).getJobs()).isEmpty();
    }

    @Test
    void deletingTheFeedLeavesThePermalinkPublishingNoJobs() throws Exception {
        feedService.delete(FEED_ENGINEERING, owner);
        commitLikeARequest();

        assertThat(document(fetch(CAREERS)).getJobs()).isEmpty();
        assertThat(permalinkService.findVisible(CAREERS, owner).getFeed()).isNull();
    }

    /**
     * A switch moves the date forward even to an older feed, so a cache that
     * revalidates with If-Modified-Since gets the new document, not a 304 (spec 5.4).
     */
    @Test
    void aSwitchIsSeenByACacheRevalidating() throws Exception {
        String before = fetch(CAREERS).getResponse().getHeader("Last-Modified");
        mvc.perform(get(url(CAREERS)).header("If-Modified-Since", before))
                .andExpect(status().isNotModified());

        // Last-Modified has whole seconds; make sure the switch lands in a later one.
        Instant stamped = ZonedDateTime.parse(before, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        while (!Instant.now().isAfter(stamped.plusSeconds(1))) {
            Thread.onSpinWait();
        }
        permalinkService.retarget(CAREERS, FEED_ALL, owner);
        commitLikeARequest();

        MvcResult after = mvc.perform(get(url(CAREERS)).header("If-Modified-Since", before))
                .andExpect(status().isOk()).andReturn();
        Instant moved = ZonedDateTime.parse(after.getResponse().getHeader("Last-Modified"),
                DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        assertThat(moved).isAfter(stamped);
        assertThat(document(after).getLastUpdated()).isAfterOrEqualTo(moved);
    }

    @Test
    void anUnknownOrMalformedPermalinkIsNotFound() throws Exception {
        mvc.perform(get(url(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
        mvc.perform(get("/ojobpub/v1/permalink/not-a-uuid/ojobpub.json"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/ojobpub/v1/permalink/careers_" + CAREERS + "/ojobpub.json"))
                .andExpect(status().isNotFound());
    }

    /** One spelling per resource (spec 5.1). */
    @Test
    void anUppercaseIdRedirectsToTheCanonicalUrl() throws Exception {
        mvc.perform(get("/ojobpub/v1/permalink/" + CAREERS.toString().toUpperCase() + "/ojobpub.json"))
                .andExpect(status().isMovedPermanently())
                .andExpect(redirectedUrl(url(CAREERS)));
    }
}
