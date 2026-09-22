package org.letsemploy.ojobpub_publisher.invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.token.ServiceTokenRepo;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invitations (spec 2.6). Authorization lives here rather than at the HTTP layer,
 * matching the rest of the application.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationService {

    /**
     * What an invite attempt did. {@link #SENT} is deliberately indistinguishable
     * from "no such account": the form must not become an oracle for which
     * addresses are registered (spec 2.6).
     */
    public enum InviteOutcome {
        SENT, ALREADY_MEMBER, ALREADY_INVITED
    }

    private final InvitationRepo invitationRepo;
    private final ServiceTokenRepo serviceTokenRepo;
    private final UserRepo userRepo;
    private final EmployerRepo employerRepo;
    private final MembershipService membershipService;

    // ------------------------------------------------------------- the invitee

    public List<Invitation> pendingFor(Actor user) {
        if (user.getId() == null) {
            return List.of();
        }
        return invitationRepo.findByInviteeIdAndStatusOrderByCreatedAtDesc(
                user.getId(), InvitationStatus.PENDING);
    }

    public long countPendingFor(Actor user) {
        return user.getId() == null ? 0
                : invitationRepo.countByInviteeIdAndStatus(user.getId(), InvitationStatus.PENDING);
    }

    /**
     * Accepting is the one act that creates a membership (spec 2.2, 2.6).
     *
     * @return the employer the user now belongs to
     */
    @Transactional
    public Employer accept(UUID invitationId, Actor user) {
        Invitation invitation = ownPending(invitationId, user);
        UserEntity invitee = invitation.getInvitee();
        membershipService.grant(invitee, invitation.getEmployer(), invitation.getRole());
        invitation.resolve(InvitationStatus.ACCEPTED);
        invitationRepo.save(invitation);
        log.info("User {} accepted the invitation to employer {}",
                invitee.getId(), invitation.getEmployer().getId());
        return invitation.getEmployer();
    }

    @Transactional
    public Employer decline(UUID invitationId, Actor user) {
        Invitation invitation = ownPending(invitationId, user);
        invitation.resolve(InvitationStatus.DECLINED);
        invitationRepo.save(invitation);
        return invitation.getEmployer();
    }

    /** Responding to someone else's invitation is a 404, never a 403 (spec 7.16). */
    private Invitation ownPending(UUID invitationId, Actor user) {
        Invitation invitation = invitationRepo.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found: " + invitationId));
        if (user.getId() == null || !invitation.getInvitee().getId().equals(user.getId())) {
            throw new NotFoundException("Invitation not found: " + invitationId);
        }
        if (!invitation.getStatus().isPending()) {
            throw new NotFoundException("Invitation already resolved: " + invitationId);
        }
        return invitation;
    }

    // --------------------------------------------------------------- the admin

    public List<Invitation> pendingForEmployer(UUID employerId) {
        return invitationRepo.findByEmployerIdAndStatusOrderByCreatedAtDesc(
                employerId, InvitationStatus.PENDING);
    }

    /**
     * Invites a registered user by exact email (spec 2.6).
     *
     * <p>An address that matches no account returns {@link InviteOutcome#SENT},
     * exactly as a successful invitation does. "Already a member" and "already
     * invited" are reported, because both people are listed on the same screen, so
     * naming them discloses nothing the admin cannot already see.
     */
    @Transactional
    public InviteOutcome invite(UUID employerId, String email, MembershipRole role, Actor actor) {
        // An owner of this employer, or an admin (spec 2.6).
        membershipService.requireOwner(actor, employerId);
        Employer employer = employerRepo.findById(employerId)
                .orElseThrow(() -> new NotFoundException("Employer not found: " + employerId));
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new ValidationFailure("email", "Enter the email address of a registered user.");
        }

        Optional<UserEntity> found = userRepo.findByEmailIgnoreCase(email.trim());
        if (found.isEmpty()) {
            // Deliberately indistinguishable from success.
            log.info("Invitation to employer {} addressed to an unregistered email", employerId);
            return InviteOutcome.SENT;
        }
        UserEntity invitee = found.get();

        if (membershipService.isMember(invitee.getId(), employerId)) {
            return InviteOutcome.ALREADY_MEMBER;
        }
        if (invitationRepo.findByEmployerIdAndInviteeIdAndStatus(
                employerId, invitee.getId(), InvitationStatus.PENDING).isPresent()) {
            return InviteOutcome.ALREADY_INVITED;
        }

        // Either a person or a token invited them, and the record says which (spec 3.11).
        MembershipRole granted = role == null ? MembershipRole.EDITOR : role;
        Invitation invitation = actor.isToken()
                ? new Invitation(employer, invitee, serviceTokenRepo.findById(actor.getId())
                        .orElseThrow(() -> new NotFoundException("Token not found: " + actor.getId())),
                        granted)
                : new Invitation(employer, invitee, userRepo.findById(actor.getId())
                        .orElseThrow(() -> new NotFoundException("User not found: " + actor.getId())),
                        granted);
        invitationRepo.save(invitation);
        log.info("{} invited user {} to employer {}", actor.getDisplayName(), invitee.getId(), employerId);
        return InviteOutcome.SENT;
    }

    @Transactional
    public void revoke(UUID invitationId, Actor actor) {
        Invitation invitation = invitationRepo.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found: " + invitationId));
        membershipService.requireOwner(actor, invitation.getEmployer().getId());
        if (!invitation.getStatus().isPending()) {
            throw new NotFoundException("Invitation already resolved: " + invitationId);
        }
        invitation.resolve(InvitationStatus.REVOKED);
        invitationRepo.save(invitation);
    }

}
