package org.letsemploy.ojobpub_publisher.tag;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepo extends JpaRepository<Tag, Long> {

    Optional<Tag> findFirstByNameIgnoreCase(String name);

    List<Tag> findAllByOrderByNameAsc();

    List<Tag> findTop20ByNameContainingIgnoreCaseOrderByNameAsc(String name);

    @Query("SELECT COUNT(j) FROM Job j JOIN j.tags t WHERE t.id = :id")
    long countJobs(@Param("id") Long id);
}
