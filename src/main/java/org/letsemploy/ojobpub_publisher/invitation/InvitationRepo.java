package org.letsemploy.ojobpub_publisher.invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepo extends JpaRepository<Invitation, UUID> {

    List<Invitation> findByInviteeIdAndStatusOrderByCreatedAtDesc(UUID inviteeId, InvitationStatus status);

    long countByInviteeIdAndStatus(UUID inviteeId, InvitationStatus status);

    List<Invitation> findByEmployerIdAndStatusOrderByCreatedAtDesc(UUID employerId, InvitationStatus status);

    Optional<Invitation> findByEmployerIdAndInviteeIdAndStatus(
            UUID employerId, UUID inviteeId, InvitationStatus status);
}
