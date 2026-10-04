package org.letsemploy.ojobpub_publisher.job;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * The publication rules of specification sections 4.3 and 4.4, in one place.
 *
 * <p>Both the feed serving path and the back-office readiness panel read these,
 * so what a screen promises and what a consumer receives cannot drift apart.
 */
public final class Publication {

    private Publication() {
    }

    /** One requirement, its message key and the form anchor that fixes it. */
    public record Requirement(String labelKey, boolean satisfied, String fixAnchor) {
    }

    /** Requirements 1-5: checked when activating, so bad data never reaches a feed. */
    public static List<Requirement> requirements(Job job) {
        List<Requirement> out = new ArrayList<>();
        out.add(new Requirement("readiness.status", job.getStatus() == JobStatus.ACTIVE, null));
        out.add(new Requirement("readiness.publishedAt", job.getPublishedAt() != null, null));
        out.add(new Requirement("readiness.required", hasRequiredFields(job), "#basics"));
        out.add(new Requirement("readiness.locations", !job.getLocations().isEmpty(), "#locations"));
        out.add(new Requirement("readiness.salary", salaryIsInterpretable(job), "#salary"));
        out.add(new Requirement("readiness.window", withinDateWindow(job, LocalDate.now()), "#dates"));
        return out;
    }

    /**
     * Requirements 1-5 only. {@code publishedAt} is excluded because it is stamped
     * by the activation itself - demanding it beforehand would make activation
     * impossible.
     */
    public static List<Requirement> activationBlockers(Job job) {
        List<Requirement> out = new ArrayList<>();
        if (!hasRequiredFields(job)) {
            out.add(new Requirement("readiness.required", false, "#basics"));
        }
        if (job.getLocations().isEmpty()) {
            out.add(new Requirement("readiness.locations", false, "#locations"));
        }
        if (!salaryIsInterpretable(job)) {
            out.add(new Requirement("readiness.salary", false, "#salary"));
        }
        return out;
    }

    public static boolean canActivate(Job job) {
        return activationBlockers(job).isEmpty();
    }

    private static boolean hasRequiredFields(Job job) {
        return notBlank(job.getTitle())
                && notBlank(job.getUrl())
                && notBlank(job.getLanguageCode())
                && job.getLanguageCode().length() == 2
                && job.getJobType() != null;
    }

    /**
     * A bare amount with no currency is not interpretable by a consumer, so
     * publishing one would be worse than publishing nothing (spec 3.3).
     */
    private static boolean salaryIsInterpretable(Job job) {
        return !job.hasSalaryAmount()
                || (notBlank(job.getSalaryCurrency()) && job.getSalaryInterval() != null);
    }

    /**
     * Requirement 6, evaluated at serving time so a stale posting leaves its feeds
     * without anyone editing it. A future start date never excludes a job (spec 4.4).
     */
    public static boolean withinDateWindow(Job job, LocalDate today) {
        if (job.getApplyBefore() != null && job.getApplyBefore().isBefore(today)) {
            return false;
        }
        return job.getEndDate() == null || !job.getEndDate().isBefore(today);
    }

    /**
     * The last day the job is published on: the earlier of its apply-by and end
     * dates, the two that close the date window, or null for neither (spec 4.4).
     */
    public static LocalDate lastDay(Job job) {
        LocalDate applyBefore = job.getApplyBefore();
        LocalDate endDate = job.getEndDate();
        if (applyBefore == null) {
            return endDate;
        }
        return endDate == null || applyBefore.isBefore(endDate) ? applyBefore : endDate;
    }

    /** A job is published only if every requirement holds. */
    public static boolean isPublishable(Job job, LocalDate today) {
        return job.getStatus() == JobStatus.ACTIVE
                && job.getPublishedAt() != null
                && hasRequiredFields(job)
                && !job.getLocations().isEmpty()
                && salaryIsInterpretable(job)
                && withinDateWindow(job, today);
    }

    /**
     * What the job presents in every list, detail and picker (spec 7.6). Exclusion
     * by date never changes the stored status, so an expired job shows as ACTIVE
     * but expired and returns by itself if the date is extended.
     */
    public static Presentation presentation(Job job, LocalDate today) {
        return switch (job.getStatus()) {
            case DRAFT -> Presentation.DRAFT;
            case INACTIVE -> Presentation.INACTIVE;
            case ACTIVE -> {
                if (job.getPublishedAt() == null || !hasRequiredFields(job)
                        || job.getLocations().isEmpty() || !salaryIsInterpretable(job)) {
                    yield Presentation.INCOMPLETE;
                }
                yield withinDateWindow(job, today) ? Presentation.PUBLISHED : Presentation.EXPIRED;
            }
        };
    }

    /**
     * The database twin of {@link #presentation(Job, LocalDate)}: the jobs that
     * present as {@code presentation} on {@code today}.
     *
     * <p>Three of the five presentation values - PUBLISHED, EXPIRED and INCOMPLETE -
     * are *derived*: all three are stored as {@code ACTIVE} and told apart by the
     * readiness rules and the date window. A list filter therefore cannot work off
     * the stored column; it has to evaluate the same rule in the database, or
     * "expired" silently returns every active job.
     *
     * <p>It lives here, beside the Java rule it mirrors, because two copies of a
     * rule in two files drift. {@code PublicationFilterTest} asserts the two agree
     * for every job in the database, so drift fails the build.
     *
     * <p>Built with the Criteria API rather than as JPQL text. As one nested JPQL
     * string it cost about nine seconds of startup and some 17 MB of heap that
     * ANTLR kept for good, because Spring Data's and Hibernate's HQL parsers both
     * worked through its five-way OR (CLAUDE.md, "Memory"). Criteria goes to
     * Hibernate's query tree without being parsed at all.
     */
    public static Specification<Job> presenting(Presentation presentation, LocalDate today) {
        return (job, query, cb) -> switch (presentation) {
            case DRAFT -> cb.equal(job.get("status"), JobStatus.DRAFT);
            case INACTIVE -> cb.equal(job.get("status"), JobStatus.INACTIVE);
            case INCOMPLETE -> cb.and(active(job, cb), cb.not(complete(job, cb)));
            case PUBLISHED -> cb.and(active(job, cb), complete(job, cb), withinWindow(job, cb, today));
            case EXPIRED -> cb.and(active(job, cb), complete(job, cb), cb.not(withinWindow(job, cb, today)));
        };
    }

    private static Predicate active(Root<Job> job, CriteriaBuilder cb) {
        return cb.equal(job.get("status"), JobStatus.ACTIVE);
    }

    /**
     * Requirements 1-5 and {@code publishedAt}, as {@link #presentation} checks
     * them. Never SQL NULL: every nullable column is tested for NULL before it is
     * measured, so {@code NOT complete} holds for an incomplete job instead of
     * dropping it as unknown.
     */
    private static Predicate complete(Root<Job> job, CriteriaBuilder cb) {
        Path<String> languageCode = job.get("languageCode");
        return cb.and(
                cb.isNotNull(job.get("publishedAt")),
                notBlank(job.get("title"), cb),
                notBlank(job.get("url"), cb),
                cb.isNotNull(languageCode),
                cb.equal(cb.length(cb.trim(languageCode)), 2),
                cb.isNotNull(job.get("jobType")),
                cb.isNotEmpty(job.<Collection<?>>get("locations")),
                cb.or(
                        cb.and(cb.isNull(job.get("salaryMin")), cb.isNull(job.get("salaryMax"))),
                        cb.and(notBlank(job.get("salaryCurrency"), cb), cb.isNotNull(job.get("salaryInterval")))));
    }

    /** {@link #withinDateWindow}: a missing date never excludes a job. */
    private static Predicate withinWindow(Root<Job> job, CriteriaBuilder cb, LocalDate today) {
        Path<LocalDate> applyBefore = job.get("applyBefore");
        Path<LocalDate> endDate = job.get("endDate");
        return cb.and(
                cb.or(cb.isNull(applyBefore), cb.greaterThanOrEqualTo(applyBefore, today)),
                cb.or(cb.isNull(endDate), cb.greaterThanOrEqualTo(endDate, today)));
    }

    private static Predicate notBlank(Path<String> value, CriteriaBuilder cb) {
        return cb.and(cb.isNotNull(value), cb.gt(cb.length(cb.trim(value)), 0));
    }

    public enum Presentation {
        PUBLISHED, EXPIRED, INCOMPLETE, DRAFT, INACTIVE;

        /** Why a member job is not in the published document (spec 7.12). */
        public String reasonKey() {
            return switch (this) {
                case PUBLISHED -> null;
                case EXPIRED -> "feed.reason.expired";
                case INCOMPLETE -> "feed.reason.incomplete";
                case DRAFT -> "feed.reason.draft";
                case INACTIVE -> "feed.reason.inactive";
            };
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
