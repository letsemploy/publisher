package org.letsemploy.ojobpub_publisher.ojobpub.v1.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.Publication;
import org.letsemploy.ojobpub_publisher.location.Location;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.dto.*;
import org.letsemploy.ojobpub_publisher.tag.Tag;
import org.springframework.stereotype.Service;

/** Builds the published document from a feed (spec 6). The single mapping point. */
@Service
@Slf4j
public class OjobpubService {

    /** The document plus what was left out of it, so the UI can explain itself (spec 5.3). */
    @Value
    public static class Result {
        OjobpubDto document;
        List<Exclusion> exclusions;
    }

    @Value
    public static class Exclusion {
        String jobId;
        String jobTitle;
        String reasonKey;
    }

    public Result generate(Feed feed, LocalDate today) {
        OjobpubDto doc = new OjobpubDto();
        doc.setEmployer(employer(feed.getEmployer()));

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
        doc.setJobs(jobs);
        doc.setLastUpdated(lastUpdated(feed, publishable));

        if (!exclusions.isEmpty()) {
            log.info("Feed {} omits {} job(s) from its published document", feed.getId(), exclusions.size());
        }
        return new Result(doc, exclusions);
    }

    /** Most recent change among the employer, the feed and the included jobs (spec 5.4). */
    private Instant lastUpdated(Feed feed, List<Job> included) {
        Instant latest = feed.getLastModifiedAt() != null ? feed.getLastModifiedAt() : Instant.EPOCH;
        if (feed.getEmployer().getLastModifiedAt() != null
                && feed.getEmployer().getLastModifiedAt().isAfter(latest)) {
            latest = feed.getEmployer().getLastModifiedAt();
        }
        for (Job job : included) {
            if (job.getLastModifiedAt() != null && job.getLastModifiedAt().isAfter(latest)) {
                latest = job.getLastModifiedAt();
            }
        }
        return latest;
    }

    private OjobpubEmployerDto employer(Employer employer) {
        OjobpubEmployerDto dto = new OjobpubEmployerDto();
        dto.setName(employer.getName());
        dto.setUrl(blankToNull(employer.getUrl()));
        dto.setIndustry(blankToNull(employer.getIndustry()));
        dto.setLocation(location(employer.getHeadquarters()));
        return dto;
    }

    private OjobpubLocationDto location(Location location) {
        return new OjobpubLocationDto(location.getCity(), location.getCountryCode());
    }

    private OjobpubJobDto job(Job job) {
        OjobpubJobDto dto = new OjobpubJobDto();
        dto.setTitle(job.getTitle());
        dto.setDescription(blankToNull(job.getDescription()));
        dto.setCategory(blankToNull(job.getCategory()));
        dto.setReferenceId(blankToNull(job.getReferenceId()));
        dto.setJobType(OjobpubEnums.jobType(job.getJobType()));
        dto.setWorkType(OjobpubEnums.workType(job.getWorkType()));
        dto.setExperienceLevel(OjobpubEnums.experienceLevel(job.getExperienceLevel()));
        dto.setPublishedAt(job.getPublishedAt());
        dto.setStartDate(job.getStartDate());
        dto.setEndDate(job.getEndDate());
        dto.setApplyBefore(job.getApplyBefore());
        dto.setLanguage(job.getLanguageCode().toLowerCase());
        dto.setUrl(job.getUrl());

        List<OjobpubLocationDto> locations = new ArrayList<>();
        job.getLocations().stream()
                .sorted(Comparator.comparing(Location::getCity))
                .forEach(l -> locations.add(location(l)));
        dto.setLocations(locations);

        // Part of the contract and the most useful signal in the document (spec 6.4).
        if (!job.getTags().isEmpty()) {
            dto.setTags(job.getTags().stream()
                    .map(Tag::getName)
                    .sorted()
                    .limit(Tag.MAX_PER_JOB)
                    .toList());
        }

        if (job.hasWorkLoad()) {
            OjobpubWorkloadDto workload = new OjobpubWorkloadDto();
            workload.setMinPercentage(job.getWorkLoadPercentMin());
            workload.setMaxPercentage(job.getWorkLoadPercentMax());
            dto.setWorkLoad(workload);
        }

        if (job.hasSalaryAmount()) {
            // Each key only if its own value is present; a missing maximum is never
            // fabricated from the minimum (spec 6.7).
            OjobpubSalaryDto salary = new OjobpubSalaryDto();
            salary.setMin(job.getSalaryMin());
            salary.setMax(job.getSalaryMax());
            salary.setCurrency(job.getSalaryCurrency() == null
                    ? null : job.getSalaryCurrency().toUpperCase());
            salary.setInterval(OjobpubEnums.salaryInterval(job.getSalaryInterval()));
            dto.setSalary(salary);
        }
        return dto;
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
