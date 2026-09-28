package org.letsemploy.ojobpub_publisher.feed;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
    @EntityGraph(attributePaths = {"employer", "employer.headquarters", "feed", "feed.jobs",
            "feed.jobs.locations", "feed.jobs.tags"})
    Optional<Permalink> findWithFeedById(UUID id);
}
