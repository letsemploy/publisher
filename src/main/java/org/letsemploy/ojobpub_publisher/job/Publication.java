package org.letsemploy.ojobpub_publisher.job;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Value;

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
    @Value
    public static class Requirement {
        String labelKey;
        boolean satisfied;
        String fixAnchor;
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
