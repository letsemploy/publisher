package org.letsemploy.ojobpub_publisher.feed;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PermalinkRepo extends JpaRepository<Permalink, UUID> {

    @EntityGraph(attributePaths = {"feed"})
    List<Permalink> findByEmployerIdOrderByNameAsc(UUID employerId);

    /** What points at a feed: named before it is deleted, and on its screen (spec 7.12). */
    List<Permalink> findByFeedIdOrderByNameAsc(UUID feedId);

    boolean existsByEmployerIdAndNameIgnoreCase(UUID employerId, String name);

    long countByEmployerId(UUID employerId);

    /**
     * The public path: the permalink, its feed, and the feed's jobs with their
     * locations and tags in a bounded number of queries, as for a feed (spec 9.3).
     */
    // Locations and tags by batch fetching, as in FeedRepo.findWithJobsById.
    @EntityGraph(attributePaths = {"employer", "employer.headquarters", "feed", "feed.jobs"})
    Optional<Permalink> findWithFeedById(UUID id);

    /**
     * A website check's answer (spec 7.23), written past the entity so the
     * permalink's last_modified_at - the served document's date - stays put. Only
     * while the host is still the one checked: an edit meanwhile wins.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Permalink p SET p.checkResult = :result, p.checkedAt = :at "
            + "WHERE p.id = :id AND p.websiteHost = :host")
    int recordCheck(@Param("id") UUID id, @Param("host") String host,
                    @Param("result") PermalinkCheck.Result result, @Param("at") Instant at);
}
