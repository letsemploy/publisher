package org.letsemploy.ojobpub_publisher.feed;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedRepo extends JpaRepository<Feed, UUID> {

    List<Feed> findByEmployerIdOrderByNameAsc(UUID employerId);

    /** The employer's {@code all} feed (spec 3.5); slugs are not unique, so the oldest. */
    Optional<Feed> findFirstByEmployerIdAndSlugOrderByCreatedAtAsc(UUID employerId, String slug);

    List<Feed> findAllByOrderByNameAsc();

    boolean existsByEmployerIdAndNameIgnoreCase(UUID employerId, String name);

    long countByEmployerId(UUID employerId);

    /**
     * Loads a feed with its jobs and each job's locations and tags in a bounded
     * number of queries: an N+1 on the serving path is a defect (spec 9.3).
     */
    // Not the jobs' locations and tags: joined too, every job row repeats per location
    // times tag. Batch fetching (default_batch_fetch_size) loads them in a few queries.
    @EntityGraph(attributePaths = {"employer", "employer.headquarters", "jobs"})
    Optional<Feed> findWithJobsById(UUID id);

    /** The feeds with a job carrying this tag, whose documents name it (spec 6). */
    @Query("SELECT DISTINCT f.id FROM Feed f JOIN f.jobs j JOIN j.tags t WHERE t.id = :tagId")
    List<UUID> findIdsByTag(@Param("tagId") Long tagId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Feed f SET f.lastModifiedAt = :now WHERE f.id IN :ids")
    int stamp(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    /**
     * Moves these feeds' date to now, for a change to their documents that writes
     * nothing of their own: a job deleted out of them, a tag renamed or deleted
     * (spec 5.4). Without it a cache revalidating by date keeps the old document.
     */
    default void contentChanged(Collection<UUID> ids) {
        if (!ids.isEmpty()) {
            stamp(ids, Instant.now());
        }
    }
}
