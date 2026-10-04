package org.letsemploy.ojobpub_publisher.summary;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.click.JobClickService;
import org.letsemploy.ojobpub_publisher.job.DashboardJobs;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.JobStatus;
import org.letsemploy.ojobpub_publisher.job.Publication;
import org.letsemploy.ojobpub_publisher.token.ServiceToken;
import org.letsemploy.ojobpub_publisher.token.TokenLifecycle;

/**
 * What one employer's part of the weekly summary says (spec 7.28): the week's
 * clicks, the jobs that left the feeds, what needs attention and the counts.
 *
 * <p>Static and clock-taking like {@link DashboardJobs}, and built on it and on
 * {@link Publication}, so the mail cannot say something the dashboard disagrees
 * with. Nothing is recorded when a job's date window closes (spec 4.4), so the
 * jobs that expired this week are derived from their dates, not read from a log.
 *
 * @param clicks          clicks over the last {@link #DAYS} days
 * @param previousClicks  clicks over the {@link #DAYS} days before those
 * @param topJobs         the most clicked jobs of the week, at most {@link #TOP_JOBS}
 * @param newlyPublished  jobs first published within the week, by title
 * @param deactivated     jobs set inactive within the week and still inactive, by title
 * @param expired         jobs whose date window closed within the week, by the day it closed
 * @param expiringTokens  the employer's tokens close to lapsing; empty unless the reader owns it
 */
public record WeeklySummary(
        UUID employerId,
        String employer,
        DashboardJobs jobs,
        long clicks,
        long previousClicks,
        List<JobClickService.JobClicks> topJobs,
        List<Job> newlyPublished,
        List<Job> deactivated,
        List<DashboardJobs.Closing> expired,
        List<ExpiringToken> expiringTokens) {

    public static final int DAYS = 7;
    public static final int TOP_JOBS = 3;

    /** A token that will stop working, and the day it does in the reader's zone. */
    public record ExpiringToken(String name, LocalDate expires) {
    }

    /**
     * @param deactivatedIds jobs that were set inactive within the week, perhaps
     *                       more than once; only those still inactive are reported
     * @param tokens         the employer's tokens, or none for a reader who is not an owner
     */
    public static WeeklySummary of(UUID employerId, String employer, List<Job> jobs,
                                   Collection<UUID> deactivatedIds, JobClickService.Statistics clicks,
                                   List<ServiceToken> tokens, int warningDays,
                                   LocalDate today, Instant now, ZoneId zone) {
        LocalDate weekStart = today.minusDays(DAYS - 1L);
        Set<UUID> deactivatedSet = Set.copyOf(deactivatedIds);
        List<Job> newlyPublished = new ArrayList<>();
        List<Job> deactivated = new ArrayList<>();
        List<DashboardJobs.Closing> expired = new ArrayList<>();
        for (Job job : jobs) {
            if (job.getPublishedAt() != null && !job.getPublishedAt().isBefore(weekStart)
                    && !job.getPublishedAt().isAfter(today)) {
                newlyPublished.add(job);
            }
            if (job.getStatus() == JobStatus.INACTIVE && deactivatedSet.contains(job.getId())) {
                deactivated.add(job);
            }
            // Expired today means its last day was yesterday: the window closes
            // after the last day, never on it.
            if (Publication.presentation(job, today) == Publication.Presentation.EXPIRED) {
                LocalDate lastDay = Publication.lastDay(job);
                if (lastDay != null && !lastDay.isBefore(today.minusDays(DAYS))) {
                    expired.add(new DashboardJobs.Closing(job, lastDay));
                }
            }
        }
        newlyPublished.sort(Comparator.comparing(Job::getTitle));
        deactivated.sort(Comparator.comparing(Job::getTitle));
        expired.sort(Comparator.comparing(DashboardJobs.Closing::lastDay).thenComparing(c -> c.job().getTitle()));

        List<ExpiringToken> expiring = tokens.stream()
                .filter(t -> TokenLifecycle.state(t, now, warningDays) == TokenLifecycle.State.EXPIRING)
                .map(t -> new ExpiringToken(t.getName(), LocalDate.ofInstant(t.getExpiresAt(), zone)))
                .sorted(Comparator.comparing(ExpiringToken::expires).thenComparing(ExpiringToken::name))
                .toList();

        List<JobClickService.JobClicks> top = clicks.topJobs().stream()
                .filter(j -> j.clicks() > 0)
                .limit(TOP_JOBS)
                .toList();

        return new WeeklySummary(employerId, employer, DashboardJobs.of(jobs, today, now),
                clicks.total(), clicks.previousTotal(), top,
                List.copyOf(newlyPublished), List.copyOf(deactivated), List.copyOf(expired), List.copyOf(expiring));
    }

    /**
     * Whether there is nothing to tell (spec 7.28): no clicks in either week, and
     * nothing that changed or needs doing. The counts alone are not news; a week
     * like that sends no mail.
     */
    public boolean isEmpty() {
        return clicks == 0 && previousClicks == 0
                && newlyPublished.isEmpty() && deactivated.isEmpty() && expired.isEmpty()
                && !jobs.needsAttention() && expiringTokens.isEmpty();
    }
}
