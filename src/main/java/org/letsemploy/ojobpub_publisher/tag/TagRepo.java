package org.letsemploy.ojobpub_publisher.tag;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query is by employer: a tag belongs to one (spec 3.4). */
public interface TagRepo extends JpaRepository<Tag, Long> {

    Optional<Tag> findFirstByEmployerIdAndNameIgnoreCase(UUID employerId, String name);

    List<Tag> findByEmployerIdOrderByNameAsc(UUID employerId);

    /** The list screen, across the employers in scope; the employer is shown per row. */
    @EntityGraph(attributePaths = "employer")
    List<Tag> findTop20ByEmployerIdInOrderByNameAsc(Collection<UUID> employerIds);

    @EntityGraph(attributePaths = "employer")
    List<Tag> findTop20ByEmployerIdInAndNameContainingIgnoreCaseOrderByNameAsc(Collection<UUID> employerIds,
                                                                              String name);

    @Query("SELECT COUNT(j) FROM Job j JOIN j.tags t WHERE t.id = :id")
    long countJobs(@Param("id") Long id);
}
