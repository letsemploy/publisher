package org.letsemploy.ojobpub_publisher.job;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobStatusEventRepo extends JpaRepository<JobStatusEvent, UUID> {

    List<JobStatusEvent> findByJobIdOrderByOccurredAtDesc(UUID jobId);
}
