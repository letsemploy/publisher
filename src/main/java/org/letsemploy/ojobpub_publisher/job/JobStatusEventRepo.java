package org.letsemploy.ojobpub_publisher.job;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobStatusEventRepo extends JpaRepository<JobStatusEvent, UUID> {

    List<JobStatusEvent> findByJobIdOrderByOccurredAtDesc(UUID jobId);

    /** One employer's changes to {@code status} since an instant, for the weekly summary (spec 7.28). */
    List<JobStatusEvent> findByJobEmployerIdAndToStatusAndOccurredAtGreaterThanEqual(
            UUID employerId, JobStatus status, Instant since);
}
