package org.letsemploy.ojobpub_publisher.membership;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.ResourceLimits;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Memberships and the rules that move them (spec 2.7).
 *
 * <p>Creating an employer and accepting an invitation are the only two acts that
 * create a membership (spec 2.2); this service owns both, so the consent rule has
 * one place to be broken and one place to be checked.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipService {

    private final MembershipRepo membershipRepo;
    private final ResourceLimits limits;

    /** People only; a token's membership is managed on its own screen (spec 7.17). */
    public List<Membership> membersOf(UUID employerId) {
        return membershipRepo.findByEmployerIdOrderByRoleAscUserDisplayNameAsc(employerId).stream()
                .filter(Membership::isHeldByUser)
                .toList();
    }

    public List<Membership> of(UUID userId) {
        return membershipRepo.findByUserId(userId);
    }

    /**
     * What a person may act on, and as what: the standing an {@link Actor} is
     * built from. A suspended membership is left out here and nowhere else, which
     * is what makes a suspension take effect everywhere at once (spec 2.7).
     */
    public Map<UUID, MembershipRole> activeRolesOf(UUID userId) {
        return membershipRepo.findByUserIdAndSuspendedAtIsNull(userId).stream()
                .collect(Collectors.toMap(m -> m.getEmployer().getId(), Membership::getRole,
                        (a, b) -> a));
    }

    /** Write site 1: the creator of an employer becomes its first owner (spec 2.7). */
    @Transactional
    public Membership createOwner(UserEntity user, Employer employer) {
        return grant(user, employer, MembershipRole.OWNER);
    }

    /**
     * Write site 2: accepting an invitation, with the role the invitation carried.
     *
     * <p>The quotas are checked inside {@code orElseGet}, not at the top: this
     * method is idempotent, and a re-grant that changes nothing must not be
     * refused for being over a limit it is not adding to (spec 8.4).
     */
    @Transactional
    public Membership grant(UserEntity user, Employer employer, MembershipRole role) {
        return membershipRepo.findByUserIdAndEmployerId(user.getId(), employer.getId())
                .orElseGet(() -> {
                    limits.requireRoomForMemberships(
                            () -> membershipRepo.countByUserId(user.getId()));
                    limits.requireRoomForMembers(
                            () -> membershipRepo.countByEmployerIdAndUserIsNotNull(employer.getId()));
                    return membershipRepo.save(new Membership(user, employer, role));
                });
    }

    /** The quota counts, for callers that refuse before doing any work (spec 8.4). */
    public long countMembershipsOf(UUID userId) {
        return membershipRepo.countByUserId(userId);
    }

    public long countMembersOf(UUID employerId) {
        return membershipRepo.countByEmployerIdAndUserIsNotNull(employerId);
    }

    public boolean isMember(UUID userId, UUID employerId) {
        return membershipRepo.existsByUserIdAndEmployerId(userId, employerId);
    }

    /**
     * May this user administer the employer itself — its record, its people?
     * An admin may, anywhere; otherwise it takes an owner membership (spec 2.1).
     */
    public boolean canAdminister(Actor user, UUID employerId) {
        return user.isAdmin() || user.isOwnerOf(employerId);
    }

    /** A non-owner gets "not found", like every other refusal here (spec 2.4). */
    public void requireOwner(Actor user, UUID employerId) {
        if (!canAdminister(user, employerId)) {
            throw new NotFoundException("Not found.");
        }
    }

    @Transactional
    public void changeRole(UUID employerId, UUID userId, MembershipRole target, Actor actor) {
        requireOwner(actor, employerId);
        Membership membership = membershipRepo.findByUserIdAndEmployerId(userId, employerId)
                .orElseThrow(() -> new NotFoundException("Membership not found."));
        if (membership.getRole() == target) {
            return;
        }
        if (target == MembershipRole.EDITOR) {
            refuseIfLastOwner(employerId, membership, "role");
        }
        membership.setRole(target);
        membershipRepo.save(membership);
        log.info("User {} set user {} to {} in employer {}",
                actor.getId(), userId, target, employerId);
    }

    @Transactional
    public void remove(UUID employerId, UUID userId, Actor actor) {
        requireOwner(actor, employerId);
        Membership membership = membershipRepo.findByUserIdAndEmployerId(userId, employerId)
                .orElseThrow(() -> new NotFoundException("Membership not found."));
        refuseIfLastOwner(employerId, membership, "member");
        membershipRepo.delete(membership);
        log.info("User {} removed user {} from employer {}", actor.getId(), userId, employerId);
    }

    /**
     * Suspend another member (spec 2.7): the membership stays, with its role, but
     * grants nothing until it is reinstated. Needs no consent, like removal.
     *
     * <p>Not oneself - an owner who locks themselves out has to find another
     * owner to let them back in - and not the last active owner, for the same
     * reason as removal.
     */
    @Transactional
    public void suspend(UUID employerId, UUID userId, Actor actor) {
        requireOwner(actor, employerId);
        Membership membership = membershipRepo.findByUserIdAndEmployerId(userId, employerId)
                .orElseThrow(() -> new NotFoundException("Membership not found."));
        if (membership.isSuspended()) {
            return;
        }
        if (userId.equals(actor.getId())) {
            throw new ValidationFailure("self", "You cannot suspend yourself.");
        }
        refuseIfLastOwner(employerId, membership, "member");
        membership.setSuspendedAt(Instant.now());
        membershipRepo.save(membership);
        log.info("User {} suspended user {} in employer {}", actor.getId(), userId, employerId);
    }

    /**
     * Lift a suspension, with the role the membership already held. Never refused
     * for a quota: a suspended membership still occupies its slot (spec 8.4).
     */
    @Transactional
    public void reinstate(UUID employerId, UUID userId, Actor actor) {
        requireOwner(actor, employerId);
        Membership membership = membershipRepo.findByUserIdAndEmployerId(userId, employerId)
                .orElseThrow(() -> new NotFoundException("Membership not found."));
        if (!membership.isSuspended()) {
            return;
        }
        membership.setSuspendedAt(null);
        membershipRepo.save(membership);
        log.info("User {} reinstated user {} in employer {}", actor.getId(), userId, employerId);
    }

    /**
     * The one rule protecting against a state nobody can repair from inside the
     * employer (spec 2.7). The message must say what to do about it — a refusal
     * with no route forward is worse than the button being missing.
     */
    private void refuseIfLastOwner(UUID employerId, Membership membership, String field) {
        if (isLastOwner(employerId, membership)) {
            throw new ValidationFailure(field,
                    "This is the only active owner. Make someone else an owner first.");
        }
    }

    /**
     * Only an active owner can be the last one: a suspended owner is already
     * unable to act, so demoting or removing them takes nothing away.
     */
    public boolean isLastOwner(UUID employerId, Membership membership) {
        return membership.getRole().isOwner() && !membership.isSuspended()
                && membershipRepo.countByEmployerIdAndRoleAndUserIsNotNullAndSuspendedAtIsNull(
                        employerId, MembershipRole.OWNER) <= 1;
    }
}
