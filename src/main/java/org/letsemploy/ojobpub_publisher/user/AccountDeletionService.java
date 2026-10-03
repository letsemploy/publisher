package org.letsemploy.ojobpub_publisher.user;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.letsemploy.ojobpub_publisher.account.LocalAccount;
import org.letsemploy.ojobpub_publisher.account.LocalAccountRepo;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditLog;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.membership.Membership;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.letsemploy.ojobpub_publisher.web.view.AccountDeletionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person deletes their own account (spec 2.13).
 *
 * <p>Every employer they are the last active owner of goes with it, with
 * everything it holds - left behind, it would have nobody to run it. Everything
 * else is the database's: memberships and invitations cascade from the user row,
 * and the tokens they created stay with their employers, forgetting who made
 * them. The audit log keeps its rows as they were (spec 3.12).
 *
 * <p>Refused for the only admin, and for a service token, which is not a person.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepo userRepo;
    private final MembershipService membershipService;
    private final EmployerService employerService;
    private final LocalAccountRepo localAccounts;
    private final AuditLog auditLog;

    public AccountDeletionService(UserRepo userRepo,
                                  MembershipService membershipService,
                                  EmployerService employerService,
                                  LocalAccountRepo localAccounts,
                                  AuditLog auditLog) {
        this.userRepo = userRepo;
        this.membershipService = membershipService;
        this.employerService = employerService;
        this.localAccounts = localAccounts;
        this.auditLog = auditLog;
    }

    /** What deleting this account would do, for the confirmation. */
    @Transactional(readOnly = true)
    public AccountDeletionView preview(Actor actor) {
        UserEntity user = self(actor);
        List<Employer> deleted = membershipService.soleOwnershipsOf(user.getId());
        Set<UUID> deletedIds = deleted.stream().map(Employer::getId).collect(Collectors.toSet());
        List<String> left = membershipService.of(user.getId()).stream()
                .map(Membership::getEmployer)
                .filter(e -> !deletedIds.contains(e.getId()))
                .map(Employer::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return new AccountDeletionView(confirmWith(user), deleted.stream().map(Employer::getName).toList(),
                left, isOnlyAdmin(user));
    }

    /**
     * Delete the actor's own account, and the employers only they own. The email
     * must be typed back; the screen asks for it, and this is where it is
     * checked, so skipping the screen does not skip the check.
     */
    @Transactional
    public void delete(Actor actor, String typed) {
        UserEntity user = self(actor);
        if (isOnlyAdmin(user)) {
            throw new ValidationFailure("admin",
                    "You are the only admin. Make someone else an admin first.");
        }
        if (typed == null || !typed.trim().equalsIgnoreCase(confirmWith(user))) {
            throw new ValidationFailure("confirmEmail", "Type your email address exactly to delete your account.");
        }
        List<Employer> soleOwnerships = membershipService.soleOwnershipsOf(user.getId());
        for (Employer employer : soleOwnerships) {
            employerService.deleteAsDepartingOwner(employer, actor);
        }
        // No foreign key joins the two: a local account's user row names it by
        // issuer and subject alone (spec 2.12). Its links cascade from it.
        if (LocalAccount.ISSUER.equals(user.getIssuer())) {
            localAccounts.deleteById(UUID.fromString(user.getSubject()));
        }
        userRepo.deleteWithEverything(user.getId());
        auditLog.record(AuditEvent.of(AuditAction.ACCOUNT_DELETED, actor).about(user)
                .target(user.getId(), user.getLabel()));
        log.info("User {} deleted their own account, and {} employer(s) only they owned",
                user.getId(), soleOwnerships.size());
    }

    private UserEntity self(Actor actor) {
        if (actor.isToken() || actor.isAnonymous()) {
            throw new NotFoundException("Not found.");
        }
        return userRepo.findById(actor.getId()).orElseThrow(() -> new NotFoundException("Not found."));
    }

    /**
     * The stored role, not admin mode: this is about who someone is, not what
     * they are doing right now (spec 2.10). A suspended admin cannot act, so does
     * not count as the other one.
     */
    private boolean isOnlyAdmin(UserEntity user) {
        return user.getRole() == UserEntity.Role.ADMIN
                && userRepo.countByRoleAndSuspendedAtIsNull(UserEntity.Role.ADMIN) <= 1;
    }

    /** The email; an account without one - an unverified provider address - types its name. */
    private static String confirmWith(UserEntity user) {
        return user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail() : user.getLabel();
    }
}
