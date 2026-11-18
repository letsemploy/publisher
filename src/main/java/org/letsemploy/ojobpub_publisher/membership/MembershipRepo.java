package org.letsemploy.ojobpub_publisher.membership;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepo extends JpaRepository<Membership, UUID> {

    List<Membership> findByUserId(UUID userId);

    /** What a person may act on: a suspended membership grants nothing (spec 2.7). */
    List<Membership> findByUserIdAndSuspendedAtIsNull(UUID userId);

    List<Membership> findByEmployerId(UUID employerId);

    Optional<Membership> findByUserIdAndEmployerId(UUID userId, UUID employerId);

    boolean existsByUserIdAndEmployerId(UUID userId, UUID employerId);

    Optional<Membership> findByServiceTokenIdAndEmployerId(UUID tokenId, UUID employerId);

    /**
     * Drives the last-owner rule (spec 2.7). Counts only owners who are *people*
     * and *active*: a workspace owned solely by a credential, or by someone locked
     * out of it, has nobody able to answer for it.
     */
    long countByEmployerIdAndRoleAndUserIsNotNullAndSuspendedAtIsNull(UUID employerId,
                                                                      MembershipRole role);

    /** How many employers this person belongs to; a token's rows have no user. */
    /**
     * The employer's active owners, locked for the rest of the transaction: the
     * last-owner rule must count them as a locking read, or two owners demoting
     * each other at once both see two and both succeed (spec 2.7). Take it as the
     * transaction's first read - see {@code MembershipService.lockActiveOwners}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Membership m WHERE m.employer.id = :employerId AND m.role = :role"
            + " AND m.user IS NOT NULL AND m.suspendedAt IS NULL ORDER BY m.id")
    List<Membership> lockActiveOwners(@Param("employerId") UUID employerId, @Param("role") MembershipRole role);

    long countByUserId(UUID userId);

    /**
     * Members of an employer who are *people* (spec 8.4). A token holds a
     * membership too, so the plain count would report one member too many.
     */
    long countByEmployerIdAndUserIsNotNull(UUID employerId);

    /** Memberships per person for a page of users, in one query (spec 7.20). */
    @Query("SELECT m.user.id, COUNT(m) FROM Membership m WHERE m.user.id IN :userIds GROUP BY m.user.id")
    List<Object[]> countByUserIds(@Param("userIds") Collection<UUID> userIds);
}
