package org.letsemploy.ojobpub_publisher.job;

import java.util.List;

/** Lifecycle state of a posting (spec 4.1). Internal only - never published. */
public enum JobStatus {
    DRAFT, ACTIVE, INACTIVE;

    /**
     * DRAFT to INACTIVE is deliberately absent: a draft nobody wants is deleted,
     * not archived.
     */
    public List<JobStatus> allowedTransitions() {
        return switch (this) {
            case DRAFT -> List.of(ACTIVE);
            case ACTIVE -> List.of(DRAFT, INACTIVE);
            case INACTIVE -> List.of(ACTIVE);
        };
    }

    public boolean canTransitionTo(JobStatus target) {
        return allowedTransitions().contains(target);
    }
}
