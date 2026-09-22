package org.letsemploy.ojobpub_publisher.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.api.ApiTypes.*;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.job.*;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jobs over the management API (spec 11.2).
 *
 * <p>Every method here calls the same {@link JobService} the back-office calls.
 * There is no second implementation of a rule: a job the API cannot activate is
 * one the screen cannot activate either.
 */
@Controller
@RequiredArgsConstructor
public class JobApiController {

    /** The same cap the back-office applies, for the same reason (spec 8.3). */
    private static final int MAX_PAGE_SIZE = 100;

    private final JobService jobService;
    private final EmployerService employerService;
    private final ApiMapper mapper;
    private final ApiActor api;
    private final ApiErrors errors;

    // ---------------------------------------------------------------- queries

    @QueryMapping
    @Transactional(readOnly = true)
    public JobPageDto jobs(@Argument int page, @Argument int size,
                           @Argument String presentation, @Argument String jobType,
                           @Argument String q) {
        api.requireScope(TokenScope.JOBS_READ);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Job> result = jobService.search(
                List.of(api.employerId()), q,
                presentation == null ? null : Publication.Presentation.valueOf(presentation),
                jobType == null ? null : JobType.valueOf(jobType),
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by("title").and(Sort.by("id"))));
        return new JobPageDto(mapper.jobs(result.getContent(), LocalDate.now()),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @QueryMapping
    @Transactional(readOnly = true)
    public JobDto job(@Argument String id) {
        Actor actor = api.requireScope(TokenScope.JOBS_READ);
        return mapper.job(jobService.findVisible(uuid(id, "job"), actor), LocalDate.now());
    }

    // -------------------------------------------------------------- mutations

    @MutationMapping
    public JobPayload createJob(@Argument Map<String, Object> input) {
        Actor actor = api.requireScope(TokenScope.JOBS_WRITE);
        Employer employer = employerService.findVisible(api.employerId(), actor);
        return save(null, input, employer, actor);
    }

    @MutationMapping
    public JobPayload updateJob(@Argument String id, @Argument Map<String, Object> input) {
        Actor actor = api.requireScope(TokenScope.JOBS_WRITE);
        Job existing = jobService.findVisible(uuid(id, "job"), actor);
        return save(existing.getId(), input, existing.getEmployer(), actor);
    }

    private JobPayload save(UUID id, Map<String, Object> input, Employer employer, Actor actor) {
        JobForm form = JobInputs.toForm(input);
        form.setId(id);
        try {
            // Never activated as a side effect of a write: that is its own act below.
            Job saved = jobService.save(form, employer, false, actor.getDisplayName());
            return JobPayload.ok(mapper.readJob(saved.getId(), actor));
        } catch (ValidationFailure e) {
            return new JobPayload(null, errors.from(e));
        }
    }

    @MutationMapping
    public JobPayload activateJob(@Argument String id) {
        return transition(id, JobStatus.ACTIVE);
    }

    @MutationMapping
    public JobPayload deactivateJob(@Argument String id) {
        return transition(id, JobStatus.INACTIVE);
    }

    /**
     * Named for the act, not for the field it happens to set: an integration that
     * asks to activate a job says so, and gets the readiness rules with it (spec 11.2).
     */
    private JobPayload transition(String id, JobStatus target) {
        Actor actor = api.requireScope(TokenScope.JOBS_WRITE);
        try {
            Job moved = jobService.transition(uuid(id, "job"), target, actor);
            return JobPayload.ok(mapper.readJob(moved.getId(), actor));
        } catch (ValidationFailure e) {
            return new JobPayload(null, errors.from(e));
        }
    }

    @MutationMapping
    public DeletePayload deleteJob(@Argument String id) {
        Actor actor = api.requireScope(TokenScope.JOBS_WRITE);
        UUID jobId = uuid(id, "job");
        jobService.delete(jobId, actor);
        return DeletePayload.ok(jobId);
    }

    /** A malformed id is "not found", not a server fault. */
    static UUID uuid(String id, String what) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("No such " + what + ": " + id);
        }
    }
}
