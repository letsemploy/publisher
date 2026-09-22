package org.letsemploy.ojobpub_publisher.job;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepo extends JpaRepository<Job, UUID> {

    @EntityGraph(attributePaths = {"employer", "locations", "tags"})
    Optional<Job> findWithDetailById(UUID id);

    /**
     * Filters on what a job *presents* as, not on the stored status: PUBLISHED,
     * EXPIRED and INCOMPLETE are all stored as ACTIVE (spec 7.6). The predicate
     * comes from {@link Publication}, beside the Java rule it mirrors.
     */
    @Query("""
            SELECT DISTINCT j FROM Job j
            LEFT JOIN j.tags t
            WHERE (:employerIds IS NULL OR j.employer.id IN :employerIds)
              AND (:q IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :q, '%'))
                             OR LOWER(j.referenceId) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:jobType IS NULL OR j.jobType = :jobType)
              AND """ + Publication.JPQL_PRESENTATION_FILTER + """
            """)
    @EntityGraph(attributePaths = {"employer", "locations", "tags"})
    Page<Job> search(@Param("employerIds") List<UUID> employerIds,
                     @Param("q") String q,
                     @Param("presentation") String presentation,
                     @Param("jobType") JobType jobType,
                     @Param("today") LocalDate today,
                     Pageable pageable);

    @EntityGraph(attributePaths = {"employer", "locations", "tags"})
    List<Job> findByEmployerIdOrderByTitleAsc(UUID employerId);

    @Query("SELECT COUNT(f) FROM Feed f JOIN f.jobs j WHERE j.id = :jobId")
    long countFeeds(@Param("jobId") UUID jobId);

    @Query("SELECT f.id FROM Feed f JOIN f.jobs j WHERE j.id = :jobId")
    List<UUID> findFeedIds(@Param("jobId") UUID jobId);

    long countByEmployerIdAndStatus(UUID employerId, JobStatus status);

    long countByEmployerId(UUID employerId);
}
