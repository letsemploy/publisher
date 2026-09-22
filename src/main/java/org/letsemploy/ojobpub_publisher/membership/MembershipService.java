package org.letsemploy.ojobpub_publisher.membership;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
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

    /** People only; a token's membership is managed on its own screen (spec 7.17). */
    public List<Membership> membersOf(UUID employerId) {
        return membershipRepo.findByEmployerIdOrderByRoleAscUserDisplayNameAsc(employerId).stream()
                .filter(Membership::isHeldByUser)
                .toList();
    }

    public List<Membership> of(UUID userId) {
        return membershipRepo.findByUserId(userId);
    }

    /** Write site 1: the creator of an employer becomes its first owner (spec 2.7). */
    @Transactional
    public Membership createOwner(UserEntity user, Employer employer) {
        return grant(user, employer, MembershipRole.OWNER);
    }

    /** Write site 2: accepting an invitation, with the role the invitation carried. */
    @Transactional
    public Membership grant(UserEntity user, Employer employer, MembershipRole role) {
        return membershipRepo.findByUserIdAndEmployerId(user.getId(), employer.getId())
                .orElseGet(() -> membershipRepo.save(new Membership(user, employer, role)));
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
     * The one rule protecting against a state nobody can repair from inside the
     * employer (spec 2.7). The message must say what to do about it — a refusal
     * with no route forward is worse than the button being missing.
     */
    private void refuseIfLastOwner(UUID employerId, Membership membership, String field) {
        if (membership.getRole().isOwner()
                && membershipRepo.countByEmployerIdAndRoleAndUserIsNotNull(employerId, MembershipRole.OWNER) <= 1) {
            throw new ValidationFailure(field,
                    "This is the only owner. Make someone else an owner first.");
        }
    }

    public boolean isLastOwner(UUID employerId, Membership membership) {
        return membership.getRole().isOwner()
                && membershipRepo.countByEmployerIdAndRoleAndUserIsNotNull(employerId, MembershipRole.OWNER) <= 1;
    }
}
