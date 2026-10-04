package org.letsemploy.ojobpub_publisher.job;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What the dashboard says about the jobs in scope (spec 7.10): how many present
 * as what, how many are new, and which need someone's attention.
 *
 * <p>Static and clock-taking like {@link Publication}, and built on it, so a
 * dashboard figure and the job list's filter cannot disagree.
 *
 * @param draft        drafts and incomplete jobs, as the dashboard has always counted them
 * @param newRecently  jobs first published within {@link #RECENT_DAYS}
 * @param closingSoon  published jobs whose last day falls within {@link #CLOSING_DAYS}, soonest first
 * @param incomplete   active jobs that cannot be published (spec 4.3), by title
 * @param staleDrafts  drafts nobody has touched for {@link #STALE_DAYS}, oldest first
 */
public record DashboardJobs(
        int published,
        int draft,
        int expired,
        int inactive,
        int total,
        int newRecently,
        List<Closing> closingSoon,
        List<Job> incomplete,
        List<Job> staleDrafts) {

    public static final int RECENT_DAYS = 30;
    public static final int CLOSING_DAYS = 14;
    public static final int STALE_DAYS = 30;

    /** A published job and the last day it is published on. */
    public record Closing(Job job, LocalDate lastDay) {
    }

    public static DashboardJobs of(List<Job> jobs, LocalDate today, Instant now) {
        int published = 0;
        int draft = 0;
        int expired = 0;
        int inactive = 0;
        int recent = 0;
        List<Closing> closing = new ArrayList<>();
        List<Job> incomplete = new ArrayList<>();
        List<Job> stale = new ArrayList<>();
        Instant staleBefore = now.minus(Duration.ofDays(STALE_DAYS));

        for (Job job : jobs) {
            Publication.Presentation presentation = Publication.presentation(job, today);
            switch (presentation) {
                case PUBLISHED -> published++;
                case EXPIRED -> expired++;
                case INCOMPLETE, DRAFT -> draft++;
                case INACTIVE -> inactive++;
            }
            if (job.getPublishedAt() != null && !job.getPublishedAt().isBefore(today.minusDays(RECENT_DAYS - 1L))
                    && !job.getPublishedAt().isAfter(today)) {
                recent++;
            }
            if (presentation == Publication.Presentation.PUBLISHED) {
                LocalDate lastDay = Publication.lastDay(job);
                if (lastDay != null && !lastDay.isAfter(today.plusDays(CLOSING_DAYS))) {
                    closing.add(new Closing(job, lastDay));
                }
            }
            if (presentation == Publication.Presentation.INCOMPLETE) {
                incomplete.add(job);
            }
            if (presentation == Publication.Presentation.DRAFT
                    && job.getLastModifiedAt() != null && job.getLastModifiedAt().isBefore(staleBefore)) {
                stale.add(job);
            }
        }
        closing.sort(Comparator.comparing(Closing::lastDay).thenComparing(c -> c.job().getTitle()));
        incomplete.sort(Comparator.comparing(Job::getTitle));
        stale.sort(Comparator.comparing(Job::getLastModifiedAt));
        return new DashboardJobs(published, draft, expired, inactive, jobs.size(), recent,
                List.copyOf(closing), List.copyOf(incomplete), List.copyOf(stale));
    }

    /** Published jobs as a share of all jobs, 0-100, for the KPI ring. */
    public int publishedShare() {
        return total == 0 ? 0 : (int) Math.round(published * 100.0 / total);
    }

    public boolean needsAttention() {
        return !closingSoon.isEmpty() || !incomplete.isEmpty() || !staleDrafts.isEmpty();
    }
}
