package org.letsemploy.ojobpub_publisher.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.feed.PermalinkService;
import org.letsemploy.ojobpub_publisher.job.JobForm;
import org.letsemploy.ojobpub_publisher.job.JobService;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.tag.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The services check who may write, whatever the caller passes (spec 2.4).
 * Every controller and resolver checks first today, so none of this is
 * reachable through them; it is what holds when the next caller forgets. A
 * stranger is refused like an unknown id, and a member is not refused at all.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class ServiceWriteScopeTest {

    /** Fixed ids from the seed: Acme, and its Senior Backend Engineer. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID JOB = UUID.fromString("8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f");

    @Autowired private JobService jobService;
    @Autowired private FeedService feedService;
    @Autowired private TagService tagService;
    @Autowired private PermalinkService permalinkService;
    @Autowired private EmployerRepo employerRepo;

    private Employer acme;
    private final Actor stranger = Actor.user(UUID.randomUUID(), "Stranger", "stranger@example.com", false,
            Map.of());
    private final Actor editor = Actor.user(UUID.randomUUID(), "Editor", "editor@example.com", false,
            Map.of(ACME, MembershipRole.EDITOR));

    @BeforeEach
    void setUp() {
        acme = employerRepo.findById(ACME).orElseThrow();
    }

    private static JobForm newJob() {
        JobForm form = new JobForm();
        form.setTitle("Written past the controller");
        form.setUrl("https://www.acme.example/jobs/past-the-controller");
        form.setLanguage("en");
        form.setJobType("permanent");
        return form;
    }

    @Test
    void aStrangerCannotCreateAJobForAnEmployer() {
        assertThatThrownBy(() -> jobService.save(newJob(), acme, false, stranger))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aStrangerCannotUpdateAJob() {
        JobForm form = newJob();
        form.setId(JOB);
        assertThatThrownBy(() -> jobService.save(form, acme, false, stranger))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aStrangerCannotCreateAFeedATagOrAPermalink() {
        assertThatThrownBy(() -> feedService.save(null, acme, "Theirs", null, null, stranger))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> tagService.create(acme, "theirs", stranger))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> permalinkService.save(null, acme, "Theirs", null, null, stranger))
                .isInstanceOf(NotFoundException.class);
    }

    /** The other direction: an editor, not an admin, writes all four. */
    @Test
    void aMemberWritesThemAll() {
        assertThat(jobService.save(newJob(), acme, false, editor).getEmployer().getId()).isEqualTo(ACME);
        assertThat(feedService.save(null, acme, "Ours", null, null, editor).getId()).isNotNull();
        assertThat(tagService.create(acme, "ours", editor).getId()).isNotNull();
        assertThat(permalinkService.save(null, acme, "Ours", null, null, editor).getId()).isNotNull();
    }
}
