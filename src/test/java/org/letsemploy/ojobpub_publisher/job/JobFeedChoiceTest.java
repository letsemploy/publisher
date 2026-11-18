package org.letsemploy.ojobpub_publisher.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.feed.FeedRepo;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.location.LocationRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which feeds a job is in, chosen where the job is written (spec 3.5, 7.11): a new
 * job without a choice goes into {@code all}, the form sets exactly what is ticked,
 * and a job ready to publish but in no feed says so.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class JobFeedChoiceTest {

    /** Fixed ids from the seed: Acme, its all feed and its Engineering feed. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID ALL = UUID.fromString("cd58289d-022e-4a1e-df83-7e5f60718293");
    private static final UUID ENGINEERING = UUID.fromString("de69390e-133f-4b2f-e094-8f6071829304");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JobService jobService;
    @Autowired
    private JobRepo jobRepo;
    @Autowired
    private FeedRepo feedRepo;
    @Autowired
    private FeedService feedService;
    @Autowired
    private EmployerRepo employerRepo;
    @Autowired
    private LocationRepo locationRepo;

    private final Actor editor = Actor.user(UUID.randomUUID(), "Erin Editor", null, false,
            Map.of(ACME, MembershipRole.EDITOR));

    private JobForm form(List<UUID> feeds) {
        JobForm form = new JobForm();
        form.setTitle("Feed choice " + UUID.randomUUID());
        form.setUrl("https://example.com/jobs/1");
        form.setLanguage("en");
        form.setJobType("permanent");
        form.setLocations(new ArrayList<>(List.of(
                locationRepo.findByEmployerIdOrderByCityAscCountryAsc(ACME).getFirst().getId())));
        form.setFeeds(feeds == null ? null : new ArrayList<>(feeds));
        return form;
    }

    private Employer acme() {
        return employerRepo.findById(ACME).orElseThrow();
    }

    @Test
    void aNewJobWithoutAChoiceGoesIntoTheAllFeed() {
        Job job = jobService.save(form(null), acme(), editor);

        assertThat(jobRepo.findFeedIds(job.getId())).containsExactly(ALL);
    }

    @Test
    void theFormSetsExactlyTheTickedFeeds() {
        Job job = jobService.save(form(List.of(ENGINEERING)), acme(), editor);
        assertThat(jobRepo.findFeedIds(job.getId())).containsExactly(ENGINEERING);

        Job none = jobService.save(form(List.of()), acme(), editor);
        assertThat(jobRepo.findFeedIds(none.getId())).isEmpty();
    }

    @Test
    void untickingOnEditRemovesTheJobAndMovesTheFeedsDate() {
        Job job = jobService.save(form(List.of(ALL, ENGINEERING)), acme(), editor);
        Instant before = Instant.now().minusSeconds(60);
        Feed engineering = feedRepo.findById(ENGINEERING).orElseThrow();
        engineering.setLastModifiedAt(before);

        JobForm edit = JobForm.of(job);
        edit.setFeeds(new ArrayList<>(List.of(ALL)));
        jobService.save(edit, acme(), editor);

        assertThat(jobRepo.findFeedIds(job.getId())).containsExactly(ALL);
        assertThat(feedRepo.findById(ENGINEERING).orElseThrow().getLastModifiedAt()).isAfter(before);
    }

    @Test
    void anEditWithoutAChoiceLeavesTheFeedsAlone() {
        Job job = jobService.save(form(List.of(ENGINEERING)), acme(), editor);

        JobForm edit = JobForm.of(job);
        edit.setFeeds(null);
        jobService.save(edit, acme(), editor);

        assertThat(jobRepo.findFeedIds(job.getId())).containsExactly(ENGINEERING);
    }

    @Test
    void anotherEmployersFeedIsRefusedOnTheFeedsField() {
        Employer other = new Employer();
        other.setName("Other AG");
        other.setSlug("other-ag");
        other = employerRepo.save(other);
        Feed foreign = feedService.createDefaultFeed(other);

        assertThatThrownBy(() -> jobService.save(form(List.of(foreign.getId())), acme(), editor))
                .isInstanceOf(ValidationFailure.class)
                .satisfies(e -> assertThat(((ValidationFailure) e).fields()).containsExactly("feeds"));
    }

    @Test
    void aReadyJobInNoFeedSaysSoAndCanBeAddedFromItsScreen() throws Exception {
        Job job = jobService.save(form(List.of()), acme(), editor);
        jobService.transition(job.getId(), JobStatus.ACTIVE, editor);

        mvc.perform(get("/jobs/" + job.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Ready, but in no feed")))
                .andExpect(content().string(Matchers.containsString("/jobs/" + job.getId() + "/feeds")));

        mvc.perform(post("/jobs/" + job.getId() + "/feeds").param("feed", ENGINEERING.toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(jobRepo.findFeedIds(job.getId())).containsExactly(ENGINEERING);

        mvc.perform(get("/jobs/" + job.getId()))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Ready, but in no feed"))));
    }

    @Test
    void theCreateFormPreselectsTheAllFeed() throws Exception {
        mvc.perform(get("/jobs/create"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.matchesPattern(
                        "(?s).*name=\"feeds\" value=\"" + ALL + "\"\\s+checked.*")));
    }
}
