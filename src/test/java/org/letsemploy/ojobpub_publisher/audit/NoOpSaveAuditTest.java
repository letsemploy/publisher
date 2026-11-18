package org.letsemploy.ojobpub_publisher.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.employer.Headquarters;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.feed.Permalink;
import org.letsemploy.ojobpub_publisher.feed.PermalinkRepo;
import org.letsemploy.ojobpub_publisher.feed.PermalinkService;
import org.letsemploy.ojobpub_publisher.job.JobForm;
import org.letsemploy.ojobpub_publisher.job.JobService;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saving a form unchanged records nothing (spec 3.12): the log is for changes,
 * and a row per idle Save would bury them. A real change still records one.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class NoOpSaveAuditTest {

    /** Fixed ids from the seed. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID ENGINEERING = UUID.fromString("de69390e-133f-4b2f-e094-8f6071829304");
    private static final UUID SENIOR_ENGINEER = UUID.fromString("8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f");

    @Autowired
    private AuditRepo auditRepo;
    @Autowired
    private EmployerRepo employerRepo;
    @Autowired
    private EmployerService employerService;
    @Autowired
    private FeedService feedService;
    @Autowired
    private PermalinkRepo permalinkRepo;
    @Autowired
    private PermalinkService permalinkService;
    @Autowired
    private JobService jobService;

    private final Actor owner = Actor.user(UUID.randomUUID(), "Owner", null, false,
            Map.of(ACME, MembershipRole.OWNER));

    private Employer acme() {
        return employerRepo.findById(ACME).orElseThrow();
    }

    private void saveEmployer(String name) {
        Employer acme = acme();
        employerService.save(ACME, name, acme.getSlug(), acme.getUrl(), acme.getIndustry(),
                Headquarters.existing(acme.getHeadquarters().getId()), owner);
    }

    @Test
    void anUnchangedEmployerRecordsNothing() {
        long rows = auditRepo.count();
        saveEmployer(acme().getName());
        assertThat(auditRepo.count()).isEqualTo(rows);
        saveEmployer("Acme renamed");
        assertThat(auditRepo.count()).isEqualTo(rows + 1);
    }

    @Test
    void anUnchangedFeedRecordsNothing() {
        Feed feed = feedService.findVisible(ENGINEERING, owner);
        long rows = auditRepo.count();
        feedService.save(ENGINEERING, acme(), feed.getName(), feed.getSlug(), feed.getDescription(), owner);
        assertThat(auditRepo.count()).isEqualTo(rows);
        feedService.save(ENGINEERING, acme(), "Engineering renamed", feed.getSlug(), feed.getDescription(), owner);
        assertThat(auditRepo.count()).isEqualTo(rows + 1);
    }

    @Test
    void anUnchangedPermalinkRecordsNothing() {
        Permalink link = permalinkRepo.findByEmployerIdOrderByNameAsc(ACME).getFirst();
        UUID feedId = link.getFeed() == null ? null : link.getFeed().getId();
        long rows = auditRepo.count();
        permalinkService.save(link.getId(), acme(), link.getName(), link.getDescription(), feedId, owner);
        assertThat(auditRepo.count()).isEqualTo(rows);
        permalinkService.save(link.getId(), acme(), "Renamed link", link.getDescription(), feedId, owner);
        assertThat(auditRepo.count()).isEqualTo(rows + 1);
    }

    @Test
    void anUnchangedJobRecordsNothing() {
        long rows = auditRepo.count();
        jobService.save(JobForm.of(jobService.findVisible(SENIOR_ENGINEER, owner)), acme(), owner);
        assertThat(auditRepo.count()).isEqualTo(rows);

        JobForm changed = JobForm.of(jobService.findVisible(SENIOR_ENGINEER, owner));
        changed.setTitle("Senior Backend Engineer (renamed)");
        jobService.save(changed, acme(), owner);
        assertThat(auditRepo.count()).isEqualTo(rows + 1);
    }
}
