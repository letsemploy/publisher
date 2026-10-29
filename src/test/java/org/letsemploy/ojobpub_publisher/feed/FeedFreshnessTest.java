package org.letsemploy.ojobpub_publisher.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.job.JobService;
import org.letsemploy.ojobpub_publisher.job.JobStatus;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.tag.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * A feed's validators move with its document (spec 5.4). Changes that write no
 * record the date is read from - a job joining or leaving, deleted, set
 * inactive, a tag renamed - must still move {@code lastUpdated}, and the ETag
 * must change with the bytes, or a revalidating cache is told 304 and keeps
 * serving the old document.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class FeedFreshnessTest {

    /** Fixed ids from the seed. Engineering holds one job, the Senior Backend Engineer. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID FEED_ENGINEERING = UUID.fromString("de69390e-133f-4b2f-e094-8f6071829304");
    private static final UUID SENIOR_ENGINEER = UUID.fromString("8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f");
    private static final UUID PRAKTIKUM = UUID.fromString("9a25f56a-dffb-478b-ac50-4b2c3d4e5f60");
    /** A tag of the Senior Backend Engineer. */
    private static final long TAG = 1L;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private FeedService feedService;
    @Autowired
    private JobService jobService;
    @Autowired
    private TagService tagService;
    @Autowired
    private EntityManager entityManager;

    private final Actor owner = Actor.user(UUID.randomUUID(), "Owner", "owner@example.com", false,
            Map.of(ACME, MembershipRole.OWNER));

    /** The feed's canonical URL, found through the redirect from a stale slug. */
    private String path() throws Exception {
        String stale = "/ojobpub/v1/x_" + ACME + "/x_" + FEED_ENGINEERING + "/ojobpub.json";
        return mvc.perform(get(stale)).andReturn().getResponse().getHeader("Location");
    }

    private MvcResult fetch() throws Exception {
        return mvc.perform(get(path())).andExpect(status().isOk()).andReturn();
    }

    /** Read as the serving request would, in a context of its own, which it then leaves. */
    private Instant lastUpdated() {
        Instant lastUpdated = feedService.publish(feedService.findForPublishing(FEED_ENGINEERING).orElseThrow())
                .document().lastUpdated();
        commitLikeARequest();
        return lastUpdated;
    }

    /** What a later request in another transaction would see. */
    private void commitLikeARequest() {
        entityManager.flush();
        entityManager.clear();
    }

    /** Last-Modified has whole seconds: wait until a change lands in a later one. */
    private static void waitPast(Instant instant) {
        while (!Instant.now().isAfter(instant.plusSeconds(1))) {
            Thread.onSpinWait();
        }
    }

    @Test
    void addingAJobMovesTheDate() {
        Instant before = lastUpdated();
        feedService.addJob(FEED_ENGINEERING, PRAKTIKUM, owner);
        commitLikeARequest();
        assertThat(lastUpdated()).isAfter(before);
    }

    @Test
    void removingAJobMovesTheDate() {
        Instant before = lastUpdated();
        feedService.removeJob(FEED_ENGINEERING, SENIOR_ENGINEER, owner);
        commitLikeARequest();
        assertThat(lastUpdated()).isAfter(before);
    }

    /** It leaves the document; the date it was dated by must not leave with it. */
    @Test
    void settingAJobInactiveMovesTheDate() {
        Instant before = lastUpdated();
        jobService.transition(SENIOR_ENGINEER, JobStatus.INACTIVE, owner);
        commitLikeARequest();
        assertThat(lastUpdated()).isAfter(before);
    }

    @Test
    void deletingAJobMovesTheDate() {
        Instant before = lastUpdated();
        jobService.delete(SENIOR_ENGINEER, owner);
        commitLikeARequest();
        assertThat(lastUpdated()).isAfter(before);
    }

    /** The document carries tag names, and a tag has no date of its own. */
    @Test
    void renamingATagMovesTheDate() {
        Instant before = lastUpdated();
        tagService.update(TAG, "renamed-for-the-test", owner);
        commitLikeARequest();
        assertThat(lastUpdated()).isAfter(before);
    }

    @Test
    void deletingATagMovesTheDate() {
        Instant before = lastUpdated();
        tagService.delete(TAG, owner);
        commitLikeARequest();
        assertThat(lastUpdated()).isAfter(before);
    }

    /** The case that went wrong: a cache revalidating by date was told 304 after a removal. */
    @Test
    void aCacheRevalidatingByDateSeesARemoval() throws Exception {
        String lastModified = fetch().getResponse().getHeader("Last-Modified");
        mvc.perform(get(path()).header("If-Modified-Since", lastModified))
                .andExpect(status().isNotModified());

        waitPast(ZonedDateTime.parse(lastModified, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
        feedService.removeJob(FEED_ENGINEERING, SENIOR_ENGINEER, owner);
        commitLikeARequest();

        mvc.perform(get(path()).header("If-Modified-Since", lastModified))
                .andExpect(status().isOk());
    }

    /** A strong ETag of the bytes: the same document is 304, a changed one is not. */
    @Test
    void theETagFollowsTheDocument() throws Exception {
        String etag = fetch().getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank().startsWith("\"").doesNotStartWith("W/");
        mvc.perform(get(path()).header("If-None-Match", etag))
                .andExpect(status().isNotModified());

        feedService.removeJob(FEED_ENGINEERING, SENIOR_ENGINEER, owner);
        commitLikeARequest();

        MvcResult after = mvc.perform(get(path()).header("If-None-Match", etag))
                .andExpect(status().isOk()).andReturn();
        assertThat(after.getResponse().getHeader("ETag")).isNotEqualTo(etag);
    }

    /**
     * With both validators, the ETag decides: a change in the same second as the
     * last fetch leaves the date where it was, and the body must still be sent.
     */
    @Test
    void theETagDecidesWhenBothAreSent() throws Exception {
        MvcResult first = fetch();
        String etag = first.getResponse().getHeader("ETag");
        String lastModified = first.getResponse().getHeader("Last-Modified");

        feedService.removeJob(FEED_ENGINEERING, SENIOR_ENGINEER, owner);
        commitLikeARequest();

        mvc.perform(get(path()).header("If-None-Match", etag).header("If-Modified-Since", lastModified))
                .andExpect(status().isOk());
    }
}
