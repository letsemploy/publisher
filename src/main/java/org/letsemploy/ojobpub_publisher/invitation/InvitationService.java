package org.letsemploy.ojobpub_publisher.invitation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditLog;
import org.letsemploy.ojobpub_publisher.common.ResourceLimits;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.validation.InputValidator;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.mail.Mail;
import org.letsemploy.ojobpub_publisher.mail.Mailer;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.token.ServiceTokenRepo;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invitations (spec 2.6). Authorization lives here rather than at the HTTP layer,
 * matching the rest of the application.
 */
@Service
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

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
    private final ResourceLimits limits;
    private final AuditLog auditLog;
    private final InputValidator inputs;
    private final Mailer mailer;
    private final String baseUrl;

    public InvitationService(InvitationRepo invitationRepo,
                             ServiceTokenRepo serviceTokenRepo,
                             UserRepo userRepo,
                             EmployerRepo employerRepo,
                             MembershipService membershipService,
                             ResourceLimits limits,
                             AuditLog auditLog,
                             InputValidator inputs,
                             Mailer mailer,
                             @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.invitationRepo = invitationRepo;
        this.serviceTokenRepo = serviceTokenRepo;
        this.userRepo = userRepo;
        this.employerRepo = employerRepo;
        this.membershipService = membershipService;
        this.limits = limits;
        this.auditLog = auditLog;
        this.inputs = inputs;
        this.mailer = mailer;
        this.baseUrl = baseUrl;
    }

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
        auditLog.record(AuditEvent.of(AuditAction.MEMBER_JOINED, user).in(invitation.getEmployer())
                .about(invitee).target(invitee.getId(), invitee.getLabel())
                .detail(invitation.getRole().name()));
        log.info("User {} accepted the invitation to employer {}",
                invitee.getId(), invitation.getEmployer().getId());
        return invitation.getEmployer();
    }

    @Transactional
    public Employer decline(UUID invitationId, Actor user) {
        Invitation invitation = ownPending(invitationId, user);
        invitation.resolve(InvitationStatus.DECLINED);
        invitationRepo.save(invitation);
        auditLog.record(AuditEvent.of(AuditAction.INVITATION_DECLINED, user).in(invitation.getEmployer())
                .about(invitation.getInvitee()).target(invitation.getInvitee().getId(), invitation.getInvitee().getLabel()));
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
        inputs.check(new InvitationInput(email));

        // Both quotas are checked BEFORE the address is looked up, and that
        // ordering is the rule, not an accident. Checked afterwards, an employer
        // at its cap would answer SENT for an unregistered address and a refusal
        // for a registered one - which is exactly the account oracle spec 2.6
        // forbids. Here the refusal depends only on the employer's own counts and
        // is identical for every address.
        limits.requireRoomForInvitations(() -> invitationRepo.countByEmployerIdAndStatus(
                employerId, InvitationStatus.PENDING));
        // An invitation that could never be accepted is worse than a refusal now.
        limits.requireRoomForMembers(() -> membershipService.countMembersOf(employerId));

        Optional<UserEntity> found = userRepo.findUniqueByEmail(email.trim());
        if (found.isEmpty()) {
            // Deliberately indistinguishable from success - for an address with no
            // account, and for one shared by several, which names nobody (spec 2.6).
            log.info("Invitation to employer {} addressed to an unregistered email", employerId);
            recordSent(employer, null, role, actor);
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
        recordSent(employer, invitee, granted, actor);
        mailInvitee(invitation);
        log.info("{} invited user {} to employer {}", actor.getDisplayName(), invitee.getId(), employerId);
        return InviteOutcome.SENT;
    }

    /**
     * Tell the invitee by mail, when mail is configured and they have not turned
     * it off (spec 2.6, 7.26). Only for an invitation that exists - an unknown
     * address gets nothing, so the form cannot be used to mail anyone at all - and
     * after commit, so a refused one mails nobody. In the invitee's language when
     * they have chosen one, else the inviter's: colleagues usually share it. A
     * courtesy, not the delivery: the invitation waits on the invitations screen
     * either way.
     */
    private void mailInvitee(Invitation invitation) {
        UserEntity invitee = invitation.getInvitee();
        if (!mailer.configured() || !invitee.isMailInvitations()) {
            return;
        }
        mailer.send(new Mail(invitee.getEmail(), "invitation", "mail.invitation.subject",
                Map.of("name", invitee.getLabel(),
                        "inviter", invitation.getInvitedByLabel(),
                        "employer", invitation.getEmployer().getName(),
                        "role", "role." + invitation.getRole().name().toLowerCase(),
                        "link", baseUrl + "/invitations"),
                invitee.getLanguage() != null ? Locale.forLanguageTag(invitee.getLanguage())
                        : LocaleContextHolder.getLocale()));
    }

    /**
     * The employer's log shows the same row whether or not the address belongs to
     * an account - no name, no address, only the role - or it would become the
     * oracle the invite form refuses to be (spec 2.6). Only the invitee's own log,
     * when there is an invitee, learns more: that it was about them.
     */
    private void recordSent(Employer employer, UserEntity invitee, MembershipRole role, Actor actor) {
        AuditEvent event = AuditEvent.of(AuditAction.INVITATION_SENT, actor).in(employer)
                .detail((role == null ? MembershipRole.EDITOR : role).name());
        auditLog.record(invitee == null ? event : event.about(invitee));
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
        UserEntity invitee = invitation.getInvitee();
        auditLog.record(AuditEvent.of(AuditAction.INVITATION_REVOKED, actor).in(invitation.getEmployer())
                .about(invitee).target(invitee.getId(), invitee.getLabel()));
    }
}
