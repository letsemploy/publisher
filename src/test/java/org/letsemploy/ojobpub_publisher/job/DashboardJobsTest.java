package org.letsemploy.ojobpub_publisher.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.neovisionaries.i18n.CountryCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.location.Location;

/** What the dashboard says about the jobs (spec 7.10). No Spring, no database. */
class DashboardJobsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

    private static Job job(String title, JobStatus status) {
        Employer employer = new Employer();
        employer.setName("Acme AG");
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setEmployer(employer);
        job.setTitle(title);
        job.setUrl("https://www.acme.example/jobs/" + title);
        job.setLanguageCode("en");
        job.setJobType(JobType.PERMANENT);
        job.setStatus(status);
        job.setLocations(Set.of(new Location("Bern", CountryCode.CH)));
        job.setLastModifiedAt(NOW);
        if (status != JobStatus.DRAFT) {
            job.setPublishedAt(TODAY.minusDays(90));
        }
        return job;
    }

    private static Job published(String title) {
        return job(title, JobStatus.ACTIVE);
    }

    @Test
    void countsWhatEachJobPresentsAs() {
        Job expired = published("Expired");
        expired.setApplyBefore(TODAY.minusDays(1));
        Job incomplete = published("Incomplete");
        incomplete.setLocations(Set.of());

        DashboardJobs jobs = DashboardJobs.of(List.of(published("A"), published("B"), expired, incomplete,
                job("Draft", JobStatus.DRAFT), job("Inactive", JobStatus.INACTIVE)), TODAY, NOW);

        assertThat(jobs.published()).isEqualTo(2);
        assertThat(jobs.expired()).isEqualTo(1);
        // Incomplete counts with the drafts, as the card always has.
        assertThat(jobs.draft()).isEqualTo(2);
        assertThat(jobs.inactive()).isEqualTo(1);
        assertThat(jobs.total()).isEqualTo(6);
        assertThat(jobs.publishedShare()).isEqualTo(33);
        assertThat(jobs.incomplete()).extracting(Job::getTitle).containsExactly("Incomplete");
    }

    /** The last 14 days, both ends included; a job already closed is expired, not closing. */
    @Test
    void closingSoonIsTheNextFourteenDaysSoonestFirst() {
        Job lastDay = published("Last day");
        lastDay.setApplyBefore(TODAY);
        Job inFourteen = published("In fourteen");
        inFourteen.setApplyBefore(TODAY.plusDays(14));
        Job inFifteen = published("In fifteen");
        inFifteen.setApplyBefore(TODAY.plusDays(15));
        Job byEndDate = published("By end date");
        // The earlier of apply-by and end date closes the window (spec 4.4).
        byEndDate.setApplyBefore(TODAY.plusDays(60));
        byEndDate.setEndDate(TODAY.plusDays(3));
        Job gone = published("Gone");
        gone.setApplyBefore(TODAY.minusDays(1));
        Job open = published("Open-ended");

        DashboardJobs jobs = DashboardJobs.of(List.of(inFourteen, open, inFifteen, gone, byEndDate, lastDay),
                TODAY, NOW);

        assertThat(jobs.closingSoon()).extracting(c -> c.job().getTitle())
                .containsExactly("Last day", "By end date", "In fourteen");
        assertThat(jobs.closingSoon()).extracting(DashboardJobs.Closing::lastDay)
                .containsExactly(TODAY, TODAY.plusDays(3), TODAY.plusDays(14));
    }

    @Test
    void aDraftIsStaleAfterThirtyDaysUntouched() {
        Job fresh = job("Fresh", JobStatus.DRAFT);
        fresh.setLastModifiedAt(NOW.minus(Duration.ofDays(29)));
        Job old = job("Old", JobStatus.DRAFT);
        old.setLastModifiedAt(NOW.minus(Duration.ofDays(31)));
        Job older = job("Older", JobStatus.DRAFT);
        older.setLastModifiedAt(NOW.minus(Duration.ofDays(200)));
        // An old job that is not a draft is not a forgotten draft.
        Job inactive = job("Inactive", JobStatus.INACTIVE);
        inactive.setLastModifiedAt(NOW.minus(Duration.ofDays(200)));

        DashboardJobs jobs = DashboardJobs.of(List.of(fresh, old, older, inactive), TODAY, NOW);

        assertThat(jobs.staleDrafts()).extracting(Job::getTitle).containsExactly("Older", "Old");
    }

    @Test
    void newMeansFirstPublishedInTheLastThirtyDays() {
        Job today = published("Today");
        today.setPublishedAt(TODAY);
        Job edge = published("Edge");
        edge.setPublishedAt(TODAY.minusDays(29));
        Job before = published("Before");
        before.setPublishedAt(TODAY.minusDays(30));

        assertThat(DashboardJobs.of(List.of(today, edge, before, job("Draft", JobStatus.DRAFT)), TODAY, NOW)
                .newRecently()).isEqualTo(2);
    }

    @Test
    void nothingNeedsAttentionWithoutJobs() {
        DashboardJobs jobs = DashboardJobs.of(List.of(), TODAY, NOW);
        assertThat(jobs.needsAttention()).isFalse();
        assertThat(jobs.publishedShare()).isZero();
    }
}
