package org.letsemploy.ojobpub_publisher.security;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepo extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByIssuerAndSubject(String issuer, String subject);

    /**
     * The Users screen (spec 7.20): everyone, by name - or those whose name or
     * email contains {@code q}, and only the suspended ones when asked (spec 2.11).
     */
    default Page<UserEntity> search(String q, boolean suspendedOnly, Pageable pageable) {
        Specification<UserEntity> spec = Specification.unrestricted();
        if (q != null && !q.isBlank()) {
            // Escaped as the derived "Containing" query this replaces did, so % and _
            // match themselves; lowered by the database on both sides, as JobService
            // does, because SQLite lowers ASCII only (spec 9.3).
            String pattern = "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("displayName")), cb.lower(cb.literal(pattern)), '\\'),
                    cb.like(cb.lower(root.get("email")), cb.lower(cb.literal(pattern)), '\\')));
        }
        if (suspendedOnly) {
            spec = spec.and((root, query, cb) -> cb.isNotNull(root.get("suspendedAt")));
        }
        return findAll(spec, pageable);
    }

    /** Exact, case-insensitive: email is how a human addresses an invitation (spec 2.6). */
    List<UserEntity> findAllByEmailIgnoreCase(String email);

    /**
     * The one account holding this address, if exactly one does (spec 2.2, 2.6).
     *
     * <p>Email is not an identity and is not unique here: two issuers may vouch for
     * the same address, and a provider may reassign one. An address held by more
     * than one account names nobody in particular, so it is treated as naming no
     * one - which for an invitation means answering exactly as for an unknown
     * address, rather than guessing or failing.
     */
    default Optional<UserEntity> findUniqueByEmail(String email) {
        List<UserEntity> found = findAllByEmailIgnoreCase(email);
        return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
    }

    /** Who could still act as an admin: the only-admin rule of spec 2.13 counts these. */
    long countByRoleAndSuspendedAtIsNull(UserEntity.Role role);

    /**
     * Deletes the account alone and lets the database's foreign keys take the
     * rest (spec 2.13): its memberships and invitations cascade, and the tokens it
     * created forget their creator. A bulk statement, as for an employer, so no
     * loaded membership is left pointing at a removed user when Hibernate flushes.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM UserEntity u WHERE u.id = :id")
    int deleteWithEverything(@Param("id") UUID id);

    /** Who has asked for the weekly summary and can receive it (spec 7.28). */
    List<UserEntity> findByMailSummaryIsTrueAndSuspendedAtIsNullAndEmailIsNotNull();

    /**
     * Claims this week's summary for one person (spec 7.28): stamps it unless it was
     * stamped after {@code due}. One statement, so of several instances running the
     * same schedule exactly one gets 1 back and sends; the others get 0.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserEntity u SET u.summarySentAt = :now"
            + " WHERE u.id = :id AND (u.summarySentAt IS NULL OR u.summarySentAt < :due)")
    int claimSummary(@Param("id") UUID id, @Param("now") Instant now,
                     @Param("due") Instant due);

}
