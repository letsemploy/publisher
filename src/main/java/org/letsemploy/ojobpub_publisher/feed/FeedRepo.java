package org.letsemploy.ojobpub_publisher.feed;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedRepo extends JpaRepository<Feed, UUID> {

    List<Feed> findByEmployerIdOrderByNameAsc(UUID employerId);

    List<Feed> findAllByOrderByNameAsc();

    boolean existsByEmployerIdAndNameIgnoreCase(UUID employerId, String name);

    long countByEmployerId(UUID employerId);

    /**
     * Loads a feed with its jobs and each job's locations and tags in a bounded
     * number of queries: an N+1 on the serving path is a defect (spec 9.3).
     */
    @EntityGraph(attributePaths = {"employer", "employer.headquarters", "jobs",
            "jobs.locations", "jobs.tags"})
    Optional<Feed> findWithJobsById(UUID id);
}
