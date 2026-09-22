package org.letsemploy.ojobpub_publisher.job;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.location.LocationService;
import org.letsemploy.ojobpub_publisher.security.AppUser;
import org.letsemploy.ojobpub_publisher.tag.Tag;
import org.letsemploy.ojobpub_publisher.tag.TagService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepo jobRepo;
    private final JobStatusEventRepo eventRepo;
    private final TagService tagService;
    private final LocationService locationService;

    /**
     * @param presentation what the job presents as (spec 7.6), not the stored
     *                     status; null means no status filter
     */
    public Page<Job> search(List<UUID> employerIds, String q, Publication.Presentation presentation,
                            JobType jobType, Pageable pageable) {
        return jobRepo.search(employerIds == null || employerIds.isEmpty() ? null : employerIds,
                q == null || q.isBlank() ? null : q.trim(),
                presentation == null ? null : presentation.name(),
                jobType, LocalDate.now(), pageable);
    }

    public Job findVisible(UUID id, AppUser user) {
        Job job = jobRepo.findWithDetailById(id)
                .orElseThrow(() -> new NotFoundException("Job not found: " + id));
        // A non-member gets 404, never 403 (spec 2.4).
        if (!user.isAdmin() && !user.getEmployerIds().contains(job.getEmployer().getId())) {
            throw new NotFoundException("Job not found: " + id);
        }
        return job;
    }

    public List<Job> findByEmployer(UUID employerId) {
        return jobRepo.findByEmployerIdOrderByTitleAsc(employerId);
    }

    public List<JobStatusEvent> history(UUID jobId) {
        return eventRepo.findByJobIdOrderByOccurredAtDesc(jobId);
    }

    public long countFeeds(UUID jobId) {
        return jobRepo.countFeeds(jobId);
    }

    /** The feeds a job belongs to, for the membership panel (spec 7.11). */
    public List<UUID> feedIdsOf(UUID jobId) {
        return jobRepo.findFeedIds(jobId);
    }

    @Transactional
    public Job save(JobForm form, Employer employer, boolean activate, String actor) {
        Job job;
        if (form.getId() == null) {
            job = new Job();
            // Set at creation and immutable thereafter (spec 3.3).
            job.setEmployer(employer);
            job.setStatus(JobStatus.DRAFT);
        } else {
            job = jobRepo.findWithDetailById(form.getId())
                    .orElseThrow(() -> new NotFoundException("Job not found: " + form.getId()));
        }

        apply(form, job);
        validate(job);
        Job saved = jobRepo.save(job);

        if (activate && saved.getStatus() != JobStatus.ACTIVE) {
            transition(saved, JobStatus.ACTIVE, actor);
        }
        return saved;
    }

    private void apply(JobForm form, Job job) {
        job.setTitle(trim(form.getTitle()));
        job.setDescription(trim(form.getDescription()));
        job.setUrl(trim(form.getUrl()));
        job.setLanguageCode(form.getLanguage() == null ? null : form.getLanguage().toLowerCase());
        job.setReferenceId(trim(form.getReferenceId()));
        job.setCategory(trim(form.getCategory()));
        job.setJobType(enumOf(JobType.class, form.getJobType()));
        job.setWorkType(enumOf(WorkType.class, form.getWorkType()));
        job.setExperienceLevel(enumOf(ExperienceLevel.class, form.getExperienceLevel()));
        job.setWorkLoadPercentMin(form.getWorkLoadPercentMin());
        job.setWorkLoadPercentMax(form.getWorkLoadPercentMax());
        job.setSalaryMin(form.getSalaryMin());
        job.setSalaryMax(form.getSalaryMax());
        job.setSalaryCurrency(form.getSalaryCurrency() == null || form.getSalaryCurrency().isBlank()
                ? null : form.getSalaryCurrency().trim().toUpperCase());
        job.setSalaryInterval(enumOf(SalaryInterval.class, form.getSalaryInterval()));
        job.setStartDate(form.getStartDate());
        job.setEndDate(form.getEndDate());
        job.setApplyBefore(form.getApplyBefore());

        job.getLocations().clear();
        for (UUID locationId : new LinkedHashSet<>(form.getLocations())) {
            job.getLocations().add(locationService.findById(locationId));
        }
        job.getTags().clear();
        for (Long tagId : new LinkedHashSet<>(form.getTags())) {
            job.getTags().add(tagService.findById(tagId));
        }
    }

    private void validate(Job job) {
        var errors = new java.util.LinkedHashMap<String, String>();
        if (isBlank(job.getTitle())) {
            errors.put("title", "A title is required.");
        }
        if (isBlank(job.getUrl())) {
            errors.put("url", "A URL is required.");
        } else if (!job.getUrl().startsWith("http://") && !job.getUrl().startsWith("https://")) {
            errors.put("url", "Must be an absolute http(s) URL.");
        }
        if (isBlank(job.getLanguageCode()) || job.getLanguageCode().length() != 2) {
            errors.put("language", "A two-letter ISO 639-1 language code is required.");
        }
        if (job.getJobType() == null) {
            errors.put("jobType", "A job type is required.");
        }
        if (job.getDescription() != null && job.getDescription().length() > 1000) {
            errors.put("description", "At most 1000 characters; the published schema caps it there.");
        }
        // A bare amount is not interpretable by a consumer (spec 3.3).
        if (job.hasSalaryAmount()) {
            if (isBlank(job.getSalaryCurrency())) {
                errors.put("salaryCurrency", "Required once an amount is given.");
            }
            if (job.getSalaryInterval() == null) {
                errors.put("salaryInterval", "Required once an amount is given.");
            }
        }
        if (job.getSalaryMin() != null && job.getSalaryMax() != null
                && job.getSalaryMin().compareTo(job.getSalaryMax()) > 0) {
            errors.put("salaryMax", "The maximum must not be below the minimum.");
        }
        if (job.getWorkLoadPercentMin() != null && job.getWorkLoadPercentMax() != null
                && job.getWorkLoadPercentMin() > job.getWorkLoadPercentMax()) {
            errors.put("workLoadPercentMax", "The maximum must not be below the minimum.");
        }
        if (job.getStartDate() != null && job.getEndDate() != null
                && job.getEndDate().isBefore(job.getStartDate())) {
            errors.put("endDate", "The end date must not precede the start date.");
        }
        if (job.getTags().size() > Tag.MAX_PER_JOB) {
            errors.put("tags", "At most " + Tag.MAX_PER_JOB + " tags.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailure(errors);
        }
    }

    /**
     * Status transition (spec 4.1). Activation is refused while a publication
     * requirement is unmet, so invalid data is stopped where a human can fix it
     * rather than silently at serving time.
     */
    @Transactional
    public Job transition(Job job, JobStatus target, String actor) {
        if (job.getStatus() == target) {
            return job;
        }
        if (!job.getStatus().canTransitionTo(target)) {
            throw new ValidationFailure("status",
                    "Cannot move from " + job.getStatus() + " to " + target + ".");
        }
        if (target == JobStatus.ACTIVE) {
            List<Publication.Requirement> blockers = Publication.activationBlockers(job);
            if (!blockers.isEmpty()) {
                var errors = new java.util.LinkedHashMap<String, String>();
                blockers.forEach(b -> errors.put(b.getLabelKey(), "Required before activation."));
                throw new ValidationFailure(errors);
            }
            // Stamped once, on first activation, and never moved afterwards (spec 4.2).
            if (job.getPublishedAt() == null) {
                job.setPublishedAt(LocalDate.now());
            }
        }
        JobStatus from = job.getStatus();
        job.setStatus(target);
        Job saved = jobRepo.save(job);
        eventRepo.save(new JobStatusEvent(saved, from, target, actor));
        log.info("Job {} moved {} -> {} by {}", saved.getId(), from, target, actor);
        return saved;
    }

    @Transactional
    public Job transition(UUID jobId, JobStatus target, AppUser user) {
        return transition(findVisible(jobId, user), target, user.getDisplayName());
    }

    @Transactional
    public void delete(UUID id, AppUser user) {
        jobRepo.delete(findVisible(id, user));
    }

    public long countByStatus(UUID employerId, JobStatus status) {
        return jobRepo.countByEmployerIdAndStatus(employerId, status);
    }

    /** Counts what each dashboard card shows, evaluating the date window (spec 7.10). */
    public int[] dashboardCounts(List<UUID> employerIds) {
        List<Job> jobs = new ArrayList<>();
        for (UUID employerId : employerIds) {
            jobs.addAll(jobRepo.findByEmployerIdOrderByTitleAsc(employerId));
        }
        LocalDate today = LocalDate.now();
        int published = 0;
        int draft = 0;
        int expired = 0;
        int inactive = 0;
        for (Job job : jobs) {
            switch (Publication.presentation(job, today)) {
                case PUBLISHED -> published++;
                case EXPIRED -> expired++;
                case INCOMPLETE, DRAFT -> draft++;
                case INACTIVE -> inactive++;
            }
        }
        return new int[]{published, draft, expired, inactive};
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Enum.valueOf(type, value.trim().toUpperCase());
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
