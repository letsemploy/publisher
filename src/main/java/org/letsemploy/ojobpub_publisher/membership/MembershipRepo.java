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

    /** Drives the last-owner rule (spec 2.7). */
    long countByEmployerIdAndRole(UUID employerId, MembershipRole role);
}
