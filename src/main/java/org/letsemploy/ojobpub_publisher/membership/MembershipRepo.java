package org.letsemploy.ojobpub_publisher.membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepo extends JpaRepository<Membership, UUID> {

    List<Membership> findByUserId(UUID userId);

    List<Membership> findByEmployerIdOrderByRoleAscUserDisplayNameAsc(UUID employerId);

    Optional<Membership> findByUserIdAndEmployerId(UUID userId, UUID employerId);

    boolean existsByUserIdAndEmployerId(UUID userId, UUID employerId);

    Optional<Membership> findByServiceTokenIdAndEmployerId(UUID tokenId, UUID employerId);

    /**
     * Drives the last-owner rule (spec 2.7). Counts only owners who are *people*:
     * a workspace owned solely by a credential has nobody answerable for it.
     */
    long countByEmployerIdAndRoleAndUserIsNotNull(UUID employerId, MembershipRole role);

    /** How many employers this person belongs to; a token's rows have no user. */
    long countByUserId(UUID userId);

    /**
     * Members of an employer who are *people* (spec 8.4). A token holds a
     * membership too, so the plain count would report one member too many.
     */
    long countByEmployerIdAndUserIsNotNull(UUID employerId);
}
