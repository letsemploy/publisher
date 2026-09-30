package org.letsemploy.ojobpub_publisher.click;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.JobRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Counts job-link clicks and answers what the dashboard shows of them (spec 5.6,
 * 7.10). Days are UTC, so a counter's day does not depend on the server's zone.
 */
@Service
public class JobClickService {

    /** Days of a job's recent trend, and of the period it is compared with (spec 7.10). */
    public static final int TREND_DAYS = 14;

    /**
     * The clicks on one job over the window, and its trend: one value per day for
     * the last {@link #TREND_DAYS}, oldest first, and the total of the days before.
     */
    public record JobClicks(Job job, long clicks, List<Long> recent, long previous) {

        public long recentTotal() {
            return recent.stream().mapToLong(Long::longValue).sum();
        }
    }

    /** The clicks from one country over the window; {@link JobClick#UNKNOWN_COUNTRY} for unknown. */
    public record CountryClicks(String country, long clicks) {
    }

    /** One day of the window, in UTC, with no clicks as zero. */
    public record Day(LocalDate day, long clicks) {
    }

    /**
     * @param daily         every day of the window, oldest first
     * @param previousTotal the clicks of the same number of days before the window
     */
    public record Statistics(int days, List<Day> daily, long previousTotal,
                             List<JobClicks> topJobs, List<CountryClicks> countries) {

        public long total() {
            return daily.stream().mapToLong(Day::clicks).sum();
        }
    }

    private final JobClickRepo clickRepo;
    private final JobRepo jobRepo;
    private final TransactionTemplate ownTransaction;
    private final List<String> ignoredAgents;
    private final int dashboardDays;
    private final int topJobs;

    public JobClickService(JobClickRepo clickRepo,
                           JobRepo jobRepo,
                           PlatformTransactionManager transactions,
                           @Value("${app.clicks.ignore-user-agents:}") String ignoredAgents,
                           @Value("${app.clicks.dashboard-days:30}") int dashboardDays,
                           @Value("${app.clicks.top-jobs:10}") int topJobs) {
        this.clickRepo = clickRepo;
        this.jobRepo = jobRepo;
        // Its own transaction, so a click is counted whatever surrounds it, and a
        // failed count can never roll back anything else.
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ignoredAgents = Arrays.stream(ignoredAgents.split(","))
                .map(s -> s.strip().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .toList();
        if (dashboardDays < 1 || topJobs < 1) {
            throw new IllegalStateException("app.clicks.dashboard-days and app.clicks.top-jobs must be at least 1");
        }
        this.dashboardDays = dashboardDays;
        this.topJobs = topJobs;
    }

    /**
     * Whether a request from this user agent is a person following the link. Link
     * previews and crawlers fetch it too, and would otherwise outnumber people.
     */
    public boolean counts(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        String agent = userAgent.toLowerCase(Locale.ROOT);
        return ignoredAgents.stream().noneMatch(agent::contains);
    }

    /**
     * One click on the job's link today, from {@code country}. Portable to both
     * databases without native SQL (spec 9.3): increment the counter, create it
     * when there is none, and when a concurrent first click created it in
     * between, increment again.
     */
    public void record(UUID jobId, UUID employerId, String country) {
        JobClick.Key key = new JobClick.Key(jobId, LocalDate.now(ZoneOffset.UTC), country);
        try {
            ownTransaction.executeWithoutResult(status -> {
                if (clickRepo.increment(key) == 0) {
                    clickRepo.saveAndFlush(new JobClick(key, employerId));
                }
            });
        } catch (DataIntegrityViolationException raced) {
            ownTransaction.executeWithoutResult(status -> clickRepo.increment(key));
        }
    }

    /**
     * The most clicked jobs and the clicks by country over the last days, for the
     * given employers only. The caller passes the employers in its scope, as for
     * every other figure on the dashboard (spec 7.10).
     */
    @Transactional(readOnly = true)
    public Statistics statistics(Collection<UUID> employerIds) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate since = today.minusDays(dashboardDays - 1L);
        if (employerIds.isEmpty()) {
            return new Statistics(dashboardDays, days(since, today, Map.of()), 0, List.of(), List.of());
        }

        Map<LocalDate, Long> perDay = clickRepo.dailyTotals(employerIds, since).stream()
                .collect(Collectors.toMap(row -> (LocalDate) row[0], row -> ((Number) row[1]).longValue()));
        long previous = clickRepo.totalBetween(employerIds, since.minusDays(dashboardDays), since);

        List<Object[]> top = clickRepo.topJobs(employerIds, since, PageRequest.of(0, topJobs));
        List<UUID> topIds = top.stream().map(row -> (UUID) row[0]).toList();
        // One query for every title and one for every trend, not one per row.
        Map<UUID, Job> jobs = jobRepo.findWithEmployerByIdIn(topIds).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
        LocalDate trendSince = today.minusDays(TREND_DAYS - 1L);
        Map<UUID, Map<LocalDate, Long>> trends = new HashMap<>();
        if (!topIds.isEmpty()) {
            for (Object[] row : clickRepo.dailyByJob(topIds, trendSince.minusDays(TREND_DAYS))) {
                trends.computeIfAbsent((UUID) row[0], id -> new HashMap<>())
                        .merge((LocalDate) row[1], ((Number) row[2]).longValue(), Long::sum);
            }
        }
        List<JobClicks> topJobs = top.stream()
                .filter(row -> jobs.containsKey((UUID) row[0]))
                .map(row -> {
                    Map<LocalDate, Long> trend = trends.getOrDefault((UUID) row[0], Map.of());
                    List<Long> recent = days(trendSince, today, trend).stream().map(Day::clicks).toList();
                    long before = trend.entrySet().stream()
                            .filter(e -> e.getKey().isBefore(trendSince))
                            .mapToLong(Map.Entry::getValue).sum();
                    return new JobClicks(jobs.get((UUID) row[0]), ((Number) row[1]).longValue(), recent, before);
                })
                .toList();

        List<CountryClicks> countries = clickRepo.byCountry(employerIds, since).stream()
                .map(row -> new CountryClicks((String) row[0], ((Number) row[1]).longValue()))
                .toList();
        return new Statistics(dashboardDays, days(since, today, perDay), previous, topJobs, countries);
    }

    /** Every day from {@code from} to {@code to}, both included, with the clicks known for it or zero. */
    private static List<Day> days(LocalDate from, LocalDate to, Map<LocalDate, Long> clicks) {
        return from.datesUntil(to.plusDays(1)).map(d -> new Day(d, clicks.getOrDefault(d, 0L))).toList();
    }
}
