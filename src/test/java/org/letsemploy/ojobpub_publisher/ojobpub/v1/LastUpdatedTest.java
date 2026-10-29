package org.letsemploy.ojobpub_publisher.ojobpub.v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.neovisionaries.i18n.CountryCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.JobStatus;
import org.letsemploy.ojobpub_publisher.job.JobType;
import org.letsemploy.ojobpub_publisher.location.Location;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.service.OjobpubService;

/**
 * What {@code lastUpdated} is made of (spec 5.4): every change to the document
 * must move it, or a consumer and a cache revalidating by date keep the old one.
 * Plain unit test: no Spring, no database.
 */
class LastUpdatedTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);
    /** Everything starts here; each test makes one thing later. */
    private static final Instant BASE = Instant.parse("2026-09-01T08:00:00Z");
    private static final Instant LATER = Instant.parse("2026-09-15T12:00:00Z");

    private final OjobpubService service = new OjobpubService("https://jobs.acme.example/", true);

    private static Location location(String city) {
        Location location = new Location(city, CountryCode.CH);
        location.setLastModifiedAt(BASE);
        return location;
    }

    private static Employer employer() {
        Employer employer = new Employer();
        employer.setName("Acme AG");
        employer.setSlug("acme-ag");
        employer.setHeadquarters(location("Bern"));
        employer.setLastModifiedAt(BASE);
        return employer;
    }

    private static Job job(Employer employer) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setEmployer(employer);
        job.setTitle("Praktikum Produktdesign");
        job.setUrl("https://www.acme.example/jobs/ACME-2026-021");
        job.setLanguageCode("de");
        job.setJobType(JobType.INTERNSHIP);
        job.setStatus(JobStatus.ACTIVE);
        job.setPublishedAt(LocalDate.of(2026, 8, 30));
        job.setLocations(Set.of(location("Bern")));
        job.setLastModifiedAt(BASE);
        return job;
    }

    private static Feed feed(Employer employer, Job... jobs) {
        Feed feed = new Feed();
        feed.setEmployer(employer);
        feed.setName("All jobs");
        feed.setSlug("all");
        feed.setLastModifiedAt(BASE);
        feed.setJobs(new LinkedHashSet<>(List.of(jobs)));
        return feed;
    }

    private Instant lastUpdated(Feed feed) {
        return service.generate(feed, TODAY).document().lastUpdated();
    }

    @Test
    void nothingChangedIsTheEarliestDate() {
        Employer employer = employer();
        assertThat(lastUpdated(feed(employer, job(employer)))).isEqualTo(BASE);
    }

    /** Set inactive, the job leaves the document: a change, though it is no longer in it. */
    @Test
    void aJobThatLeftTheDocumentStillCounts() {
        Employer employer = employer();
        Job job = job(employer);
        job.setStatus(JobStatus.INACTIVE);
        job.setLastModifiedAt(LATER);
        Feed feed = feed(employer, job);

        assertThat(service.generate(feed, TODAY).document().jobs()).isEmpty();
        assertThat(lastUpdated(feed)).isEqualTo(LATER);
    }

    /** Nothing is written when a window closes, so the day after the last one is the change. */
    @Test
    void aClosedWindowCountsFromTheDayAfterTheLastDay() {
        Employer employer = employer();
        Job job = job(employer);
        job.setApplyBefore(LocalDate.of(2026, 9, 19));

        assertThat(lastUpdated(feed(employer, job))).isEqualTo(
                LocalDate.of(2026, 9, 20).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** A window closing today has not closed yet: the job is still published today. */
    @Test
    void aWindowClosingTodayIsNoChangeYet() {
        Employer employer = employer();
        Job job = job(employer);
        job.setApplyBefore(TODAY);

        assertThat(lastUpdated(feed(employer, job))).isEqualTo(BASE);
    }

    @Test
    void aRenamedLocationOfAnIncludedJobCounts() {
        Employer employer = employer();
        Job job = job(employer);
        job.getLocations().iterator().next().setLastModifiedAt(LATER);

        assertThat(lastUpdated(feed(employer, job))).isEqualTo(LATER);
    }

    @Test
    void aChangedHeadquartersCounts() {
        Employer employer = employer();
        employer.getHeadquarters().setLastModifiedAt(LATER);

        assertThat(lastUpdated(feed(employer, job(employer)))).isEqualTo(LATER);
    }
}
