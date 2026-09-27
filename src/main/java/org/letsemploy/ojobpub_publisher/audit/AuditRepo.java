package org.letsemploy.ojobpub_publisher.audit;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditRepo extends JpaRepository<AuditEvent, UUID> {

    /** Everything, for platform staff. */
    Page<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    /**
     * The logs of several employers at once, as the viewer may read them: all of
     * an employer they administer, and of one they only belong to, everything but
     * the owners-only actions (spec 3.12). Filtered here, not after loading, so a
     * page is always full and the count is right.
     */
    @Query("""
            select e from AuditEvent e
            where e.employerId in :administered
               or (e.employerId in :member and e.action not in :ownersOnly)
            order by e.occurredAt desc""")
    Page<AuditEvent> findForEmployers(@Param("administered") Collection<UUID> administered,
                                      @Param("member") Collection<UUID> member,
                                      @Param("ownersOnly") Collection<AuditAction> ownersOnly,
                                      Pageable pageable);

    /** A person's own log: what was done to them, and what they did (spec 3.12). */
    @Query("""
            select e from AuditEvent e
            where e.subjectUserId = :userId or e.actorUserId = :userId
            order by e.occurredAt desc""")
    Page<AuditEvent> findForPerson(@Param("userId") UUID userId, Pageable pageable);
}
