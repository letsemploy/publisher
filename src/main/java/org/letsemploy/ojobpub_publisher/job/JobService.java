package org.letsemploy.ojobpub_publisher.job;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditLog;
import org.letsemploy.ojobpub_publisher.common.ResourceLimits;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.common.validation.InputValidator;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.location.LocationService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.tag.Tag;
import org.letsemploy.ojobpub_publisher.tag.TagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepo jobRepo;
    private final JobStatusEventRepo eventRepo;
    private final TagService tagService;
    private final LocationService locationService;
    private final ResourceLimits limits;
    private final AuditLog auditLog;
    private final InputValidator inputs;
    private final MessageSource messages;

    public JobService(JobRepo jobRepo,
                      JobStatusEventRepo eventRepo,
                      TagService tagService,
                      LocationService locationService,
                      ResourceLimits limits,
                      AuditLog auditLog,
                      InputValidator inputs,
                      MessageSource messages) {
        this.jobRepo = jobRepo;
        this.eventRepo = eventRepo;
        this.tagService = tagService;
        this.locationService = locationService;
        this.limits = limits;
        this.auditLog = auditLog;
        this.inputs = inputs;
        this.messages = messages;
    }

    /**
     * @param presentation what the job presents as (spec 7.6), not the stored
     *                     status; null means no status filter
     */
    public Page<Job> search(List<UUID> employerIds, String q, Publication.Presentation presentation,
                            JobType jobType, Pageable pageable) {
        List<Specification<Job>> filters = new ArrayList<>();
        if (employerIds != null && !employerIds.isEmpty()) {
            filters.add((job, query, cb) -> job.get("employer").get("id").in(employerIds));
        }
        if (q != null && !q.isBlank()) {
            // Lowered by the database on both sides, as before: SQLite lowers ASCII
            // only, and a pattern lowered in Java would stop matching there (spec 9.3).
            String pattern = "%" + q.trim() + "%";
            filters.add((job, query, cb) -> cb.or(
                    cb.like(cb.lower(job.get("title")), cb.lower(cb.literal(pattern))),
                    cb.like(cb.lower(job.get("referenceId")), cb.lower(cb.literal(pattern)))));
        }
        if (jobType != null) {
            filters.add((job, query, cb) -> cb.equal(job.get("jobType"), jobType));
        }
        if (presentation != null) {
            filters.add(Publication.presenting(presentation, LocalDate.now()));
        }
        return jobRepo.findAll(Specification.allOf(filters), pageable);
    }

    public Job findVisible(UUID id, Actor user) {
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

    /** Feed counts for a whole page in one query, keyed by job id (spec 10). */
    public Map<UUID, Long> feedCounts(List<Job> jobs) {
        if (jobs.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> counts = new java.util.HashMap<>();
        for (Object[] row : jobRepo.countFeedsByJob(jobs.stream().map(Job::getId).toList())) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** The feeds a job belongs to, for the membership panel (spec 7.11). */
    public List<UUID> feedIdsOf(UUID jobId) {
        return jobRepo.findFeedIds(jobId);
    }

    @Transactional
    public Job save(JobForm form, Employer employer, boolean activate, Actor actor) {
        Job job;
        if (form.getId() == null) {
            limits.requireRoomForJobs(() -> jobRepo.countByEmployerId(employer.getId()));
            job = new Job();
            // Set at creation and immutable thereafter (spec 3.3).
            job.setEmployer(employer);
            job.setStatus(JobStatus.DRAFT);
        } else {
            job = jobRepo.findWithDetailById(form.getId())
                    .orElseThrow(() -> new NotFoundException("Job not found: " + form.getId()));
        }

        form.trim();
        apply(form, job);
        validate(form, job);
        Job saved = jobRepo.save(job);
        auditLog.record(AuditEvent.of(form.getId() == null ? AuditAction.JOB_CREATED : AuditAction.JOB_UPDATED,
                actor).in(employer).target(saved.getId(), saved.getTitle()));

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

        // Only the job's own employer's locations and tags (spec 3.3) - for the form
        // and the API alike, since both arrive here. Another employer's id is
        // refused the same way as one that does not exist.
        job.getLocations().clear();
        for (UUID locationId : new LinkedHashSet<>(form.getLocations())) {
            job.getLocations().add(locationService.requireOwn(locationId, job.getEmployer(), "locations"));
        }
        job.getTags().clear();
        for (Long tagId : new LinkedHashSet<>(form.getTags())) {
            job.getTags().add(tagService.requireOwn(tagId, job.getEmployer(), "tags"));
        }
    }

    /**
     * The form's shape (spec 9.6), after {@link #apply}: another employer's
     * location or tag is refused there first, as the API's field names expect.
     * The tag count stays here, because it counts what survived de-duplication
     * and the ownership check.
     */
    private void validate(JobForm form, Job job) {
        Map<String, String> errors = inputs.violations(form);
        if (job.getTags().size() > Tag.MAX_PER_JOB) {
            errors.put("tags", messages.getMessage("validation.tags.tooMany",
                    new Object[]{Tag.MAX_PER_JOB}, LocaleContextHolder.getLocale()));
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
    public Job transition(Job job, JobStatus target, Actor actor) {
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
                blockers.forEach(b -> errors.put(b.labelKey(), "Required before activation."));
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
        eventRepo.save(new JobStatusEvent(saved, from, target, actor.getDisplayName()));
        auditLog.record(AuditEvent.of(AuditAction.JOB_STATUS_CHANGED, actor).in(saved.getEmployer())
                .target(saved.getId(), saved.getTitle()).detail(from + " → " + target));
        log.info("Job {} moved {} -> {} by {}", saved.getId(), from, target, actor.getDisplayName());
        return saved;
    }

    @Transactional
    public Job transition(UUID jobId, JobStatus target, Actor user) {
        return transition(findVisible(jobId, user), target, user);
    }

    @Transactional
    public void delete(UUID id, Actor user) {
        Job job = findVisible(id, user);
        jobRepo.delete(job);
        auditLog.record(AuditEvent.of(AuditAction.JOB_DELETED, user).in(job.getEmployer())
                .target(job.getId(), job.getTitle()));
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

}
