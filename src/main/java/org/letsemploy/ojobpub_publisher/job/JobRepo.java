package org.letsemploy.ojobpub_publisher.job;

import java.time.LocalDate;
import java.util.Collection;
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
            SELECT j FROM Job j
            WHERE (:employerIds IS NULL OR j.employer.id IN :employerIds)
              AND (:q IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :q, '%'))
                             OR LOWER(j.referenceId) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:jobType IS NULL OR j.jobType = :jobType)
              AND """ + Publication.JPQL_PRESENTATION_FILTER + """
            """)
    // Only the to-one association is fetch-joined. Fetch-joining a *collection*
    // alongside a Pageable forces Hibernate to drop the SQL LIMIT and paginate in
    // memory (HHH90003004) - it loads every matching row to return twenty. The
    // rows need `locations`, which arrives through batch fetching instead:
    // hibernate.default_batch_fetch_size keeps that to a bounded number of queries.
    @EntityGraph(attributePaths = {"employer"})
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

    /**
     * Feed counts for a whole page in one query. Calling {@link #countFeeds} per
     * row is an N+1: twenty rows meant twenty queries.
     */
    @Query("SELECT j.id, COUNT(f) FROM Feed f JOIN f.jobs j WHERE j.id IN :jobIds GROUP BY j.id")
    List<Object[]> countFeedsByJob(@Param("jobIds") Collection<UUID> jobIds);

    @Query("SELECT f.id FROM Feed f JOIN f.jobs j WHERE j.id = :jobId")
    List<UUID> findFeedIds(@Param("jobId") UUID jobId);

    long countByEmployerIdAndStatus(UUID employerId, JobStatus status);

    long countByEmployerId(UUID employerId);
}
