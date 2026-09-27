package org.letsemploy.ojobpub_publisher.employer;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployerRepo extends JpaRepository<Employer, UUID> {

    Page<Employer> findByIdInOrderByNameAsc(List<UUID> ids, Pageable pageable);

    Page<Employer> findAllByOrderByNameAsc(Pageable pageable);

    List<Employer> findAllByOrderByNameAsc();

    Page<Employer> findByNameContainingIgnoreCaseOrderByNameAsc(String name, Pageable pageable);

    /**
     * Deletes the row alone and lets the database's foreign keys take the rest:
     * jobs, feeds, locations, tags, memberships, invitations and tokens all
     * cascade from it (spec 3.2). A bulk statement rather than {@code delete(entity)}:
     * the employer's headquarters is loaded with it, and Hibernate would refuse to
     * flush a location that still points at an employer it has just removed. The
     * session is cleared afterwards, so nothing stale survives the request.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Employer e WHERE e.id = :id")
    int deleteWithEverything(@Param("id") UUID id);
}
