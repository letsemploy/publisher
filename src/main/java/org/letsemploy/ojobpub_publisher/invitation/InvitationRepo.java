package org.letsemploy.ojobpub_publisher.invitation;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepo extends JpaRepository<Invitation, UUID> {

    /** Locked ({@code FOR UPDATE}) to answer it: a double click must not answer it twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invitation i WHERE i.id = :id")
    Optional<Invitation> lockById(@Param("id") UUID id);

    List<Invitation> findByInviteeIdAndStatusOrderByCreatedAtDesc(UUID inviteeId, InvitationStatus status);

    long countByInviteeIdAndStatus(UUID inviteeId, InvitationStatus status);

    long countByEmployerIdAndStatus(UUID employerId, InvitationStatus status);

    List<Invitation> findByEmployerIdAndStatusOrderByCreatedAtDesc(UUID employerId, InvitationStatus status);

    Optional<Invitation> findByEmployerIdAndInviteeIdAndStatus(
            UUID employerId, UUID inviteeId, InvitationStatus status);
}
