package org.letsemploy.ojobpub_publisher.ojobpub.v1.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.Publication;
import org.letsemploy.ojobpub_publisher.location.Location;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.dto.*;
import org.letsemploy.ojobpub_publisher.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Builds the published document from a feed (spec 6), or from none (spec 5.5). The single mapping point. */
@Service
public class OjobpubService {

    private static final Logger log = LoggerFactory.getLogger(OjobpubService.class);

    /** The document plus what was left out of it, so the UI can explain itself (spec 5.3). */
    public record Result(OjobpubDto document, List<Exclusion> exclusions) {
    }

    public record Exclusion(String jobId, String jobTitle, String reasonKey) {
    }

    private final String baseUrl;
    private final boolean jobLinks;

    /**
     * @param baseUrl  the application's public address, so a job link is absolute (spec 9.5)
     * @param jobLinks whether a job's URL is its link through this application,
     *                 which counts clicks and closes with the job (spec 5.6), or
     *                 the employer's own page
     */
    public OjobpubService(@Value("${app.base-url}") String baseUrl,
                          @Value("${app.clicks.enabled:true}") boolean jobLinks) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.jobLinks = jobLinks;
    }

    public Result generate(Feed feed, LocalDate today) {
        List<Exclusion> exclusions = new ArrayList<>();
        List<Job> publishable = new ArrayList<>();
        for (Job job : feed.getJobs()) {
            if (Publication.isPublishable(job, today)) {
                publishable.add(job);
            } else {
                Publication.Presentation p = Publication.presentation(job, today);
                exclusions.add(new Exclusion(job.getId().toString(), job.getTitle(), p.reasonKey()));
            }
        }

        // Stable order between fetches, so a consumer diff shows real changes only (spec 5.2).
        publishable.sort(Comparator.comparing(Job::getPublishedAt).reversed()
                .thenComparing(Job::getTitle));

        List<OjobpubJobDto> jobs = new ArrayList<>();
        for (Job job : publishable) {
            jobs.add(job(job));
        }
        OjobpubDto doc = new OjobpubDto(
                lastUpdated(feed.getEmployer(), feed.getLastModifiedAt(), feed.getJobs(), publishable, today),
                employer(feed.getEmployer()), jobs);

        if (!exclusions.isEmpty()) {
            log.info("Feed {} omits {} job(s) from its published document", feed.getId(), exclusions.size());
        }
        return new Result(doc, exclusions);
    }

    /**
     * The employer with no jobs: what a permalink without a feed publishes (spec
     * 5.5). A valid document rather than an error, so taking every posting down
     * is a switch, and nothing reading the URL sees it break.
     *
     * @param since when the permalink last changed, so switching to nothing is
     *              itself a change a cache must see (spec 5.4)
     */
    public Result generateEmpty(Employer employer, Instant since) {
        OjobpubDto doc = new OjobpubDto(
                lastUpdated(employer, since, List.of(), List.of(), LocalDate.now()), employer(employer), List.of());
        return new Result(doc, List.of());
    }

    /**
     * The most recent change to anything the document is made of (spec 5.4). A
     * consumer and a cache revalidating with {@code If-Modified-Since} both read
     * an unchanged date as an unchanged document, so every way the document can
     * change must move it:
     * <ul>
     *   <li>the employer, its headquarters, and the feed or permalink itself, whose
     *       date also moves when a job joins or leaves it (spec 3.5);</li>
     *   <li>every job in the feed, included or not, since a job that stops being
     *       published changes the document by leaving it;</li>
     *   <li>the locations of the included jobs;</li>
     *   <li>the start of the day after a job's window closed: nothing is written
     *       when it closes (spec 4.4), yet the job leaves the document then.</li>
     * </ul>
     */
    private Instant lastUpdated(Employer employer, Instant source, Collection<Job> inFeed,
                                List<Job> included, LocalDate today) {
        List<Instant> changes = new ArrayList<>();
        changes.add(source);
        changes.add(employer.getLastModifiedAt());
        if (employer.getHeadquarters() != null) {
            changes.add(employer.getHeadquarters().getLastModifiedAt());
        }
        for (Job job : inFeed) {
            changes.add(job.getLastModifiedAt());
            LocalDate lastDay = Publication.lastDay(job);
            if (lastDay != null && lastDay.isBefore(today)) {
                changes.add(lastDay.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            }
        }
        for (Job job : included) {
            for (Location location : job.getLocations()) {
                changes.add(location.getLastModifiedAt());
            }
        }
        return changes.stream().filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(Instant.EPOCH);
    }

    private OjobpubEmployerDto employer(Employer employer) {
        return new OjobpubEmployerDto(employer.getName(), blankToNull(employer.getUrl()),
                blankToNull(employer.getIndustry()), location(employer.getHeadquarters()));
    }

    private OjobpubLocationDto location(Location location) {
        return new OjobpubLocationDto(location.getCity(), location.getCountryCode());
    }

    private OjobpubJobDto job(Job job) {
        List<OjobpubLocationDto> locations = job.getLocations().stream()
                .sorted(Comparator.comparing(Location::getCity))
                .map(this::location)
                .toList();

        // Part of the contract and the most useful signal in the document (spec 6.4).
        List<String> tags = job.getTags().isEmpty() ? null : job.getTags().stream()
                .map(Tag::getName)
                .sorted()
                .limit(Tag.MAX_PER_JOB)
                .toList();

        OjobpubWorkloadDto workload = job.hasWorkLoad()
                ? new OjobpubWorkloadDto(job.getWorkLoadPercentMin(), job.getWorkLoadPercentMax())
                : null;

        // Each key only if its own value is present; a missing maximum is never
        // fabricated from the minimum (spec 6.7).
        OjobpubSalaryDto salary = job.hasSalaryAmount()
                ? new OjobpubSalaryDto(job.getSalaryMin(), job.getSalaryMax(),
                        job.getSalaryCurrency() == null ? null : job.getSalaryCurrency().toUpperCase(),
                        OjobpubEnums.salaryInterval(job.getSalaryInterval()))
                : null;

        return new OjobpubJobDto(
                job.getTitle(),
                blankToNull(job.getDescription()),
                blankToNull(job.getCategory()),
                blankToNull(job.getReferenceId()),
                OjobpubEnums.jobType(job.getJobType()),
                OjobpubEnums.workType(job.getWorkType()),
                OjobpubEnums.experienceLevel(job.getExperienceLevel()),
                workload,
                salary,
                locations,
                job.getPublishedAt(),
                job.getStartDate(),
                job.getEndDate(),
                job.getApplyBefore(),
                job.getLanguageCode().toLowerCase(),
                jobLinks ? baseUrl + "/go/" + job.getId() : job.getUrl(),
                tags);
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
