package org.letsemploy.ojobpub_publisher.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.neovisionaries.i18n.CountryCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.click.JobClickService;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.JobStatus;
import org.letsemploy.ojobpub_publisher.job.JobType;
import org.letsemploy.ojobpub_publisher.location.Location;
import org.letsemploy.ojobpub_publisher.token.ServiceToken;

/** What one employer's part of the weekly summary says (spec 7.28). No Spring, no database. */
class WeeklySummaryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final Instant NOW = Instant.parse("2026-10-05T07:00:00Z");
    private static final UUID EMPLOYER = UUID.randomUUID();

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

    private static Job closedOn(String title, LocalDate lastDay) {
        Job job = job(title, JobStatus.ACTIVE);
        job.setApplyBefore(lastDay);
        return job;
    }

    private static JobClickService.Statistics clicks(long total, long previous) {
        List<JobClickService.Day> daily = total == 0 ? List.of()
                : List.of(new JobClickService.Day(TODAY, total));
        return new JobClickService.Statistics(WeeklySummary.DAYS, daily, previous, List.of(), List.of());
    }

    private static WeeklySummary summary(List<Job> jobs, Set<UUID> deactivated, JobClickService.Statistics clicks,
                                         List<ServiceToken> tokens) {
        return WeeklySummary.of(EMPLOYER, "Acme AG", jobs, deactivated, clicks, tokens, 30,
                TODAY, NOW, ZoneOffset.UTC);
    }

    /** A last day yesterday through seven days ago closed this week; today is still open. */
    @Test
    void expiredIsTheWindowsThatClosedThisWeek() {
        Job yesterday = closedOn("Yesterday", TODAY.minusDays(1));
        Job weekAgo = closedOn("A week ago", TODAY.minusDays(7));
        Job longAgo = closedOn("Long ago", TODAY.minusDays(8));
        Job lastDayToday = closedOn("Today", TODAY);

        WeeklySummary summary = summary(List.of(yesterday, weekAgo, longAgo, lastDayToday), Set.of(),
                clicks(0, 0), List.of());

        assertThat(summary.expired()).extracting(c -> c.job().getTitle())
                .containsExactly("A week ago", "Yesterday");
    }

    /** Only jobs still inactive: one reactivated since has nothing to report. */
    @Test
    void deactivatedAreTheJobsStillInactive() {
        Job inactive = job("Inactive", JobStatus.INACTIVE);
        Job reactivated = job("Back again", JobStatus.ACTIVE);
        Job inactiveForAges = job("Inactive for ages", JobStatus.INACTIVE);

        WeeklySummary summary = summary(List.of(inactive, reactivated, inactiveForAges),
                Set.of(inactive.getId(), reactivated.getId()), clicks(0, 0), List.of());

        assertThat(summary.deactivated()).extracting(Job::getTitle).containsExactly("Inactive");
    }

    @Test
    void newlyPublishedIsTheLastSevenDays() {
        Job today = job("Today", JobStatus.ACTIVE);
        today.setPublishedAt(TODAY);
        Job sixDays = job("Six days ago", JobStatus.ACTIVE);
        sixDays.setPublishedAt(TODAY.minusDays(6));
        Job sevenDays = job("Seven days ago", JobStatus.ACTIVE);
        sevenDays.setPublishedAt(TODAY.minusDays(7));

        WeeklySummary summary = summary(List.of(today, sixDays, sevenDays), Set.of(), clicks(0, 0), List.of());

        assertThat(summary.newlyPublished()).extracting(Job::getTitle).containsExactly("Six days ago", "Today");
    }

    /** Counts alone are not news: a quiet week of published jobs sends nothing. */
    @Test
    void aQuietWeekIsEmpty() {
        assertThat(summary(List.of(job("Steady", JobStatus.ACTIVE)), Set.of(), clicks(0, 0), List.of()).isEmpty())
                .isTrue();
        assertThat(summary(List.of(), Set.of(), clicks(3, 0), List.of()).isEmpty()).isFalse();
        // Down to none from some is news too.
        assertThat(summary(List.of(), Set.of(), clicks(0, 4), List.of()).isEmpty()).isFalse();
        Job incomplete = job("Incomplete", JobStatus.ACTIVE);
        incomplete.setLocations(Set.of());
        assertThat(summary(List.of(incomplete), Set.of(), clicks(0, 0), List.of()).isEmpty()).isFalse();
    }

    /** Within the warning days, by the day it lapses; a far-off or revoked token is not mentioned. */
    @Test
    void tokensAboutToExpire() {
        ServiceToken soon = token("Soon", NOW.plus(Duration.ofDays(10)), null);
        ServiceToken later = token("Later", NOW.plus(Duration.ofDays(200)), null);
        ServiceToken revoked = token("Revoked", NOW.plus(Duration.ofDays(5)), NOW.minus(Duration.ofDays(1)));

        WeeklySummary summary = summary(List.of(), Set.of(), clicks(0, 0), List.of(soon, later, revoked));

        assertThat(summary.expiringTokens()).containsExactly(
                new WeeklySummary.ExpiringToken("Soon", TODAY.plusDays(10)));
        assertThat(summary.isEmpty()).isFalse();
    }

    private static ServiceToken token(String name, Instant expiresAt, Instant revokedAt) {
        ServiceToken token = new ServiceToken();
        token.setName(name);
        token.setExpiresAt(expiresAt);
        token.setRevokedAt(revokedAt);
        return token;
    }
}
