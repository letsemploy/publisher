package org.letsemploy.ojobpub_publisher.user;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.net.URLEncoder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditLog;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.common.validation.InputValidator;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.letsemploy.ojobpub_publisher.web.view.Avatar;
import org.letsemploy.ojobpub_publisher.web.view.PageView;
import org.letsemploy.ojobpub_publisher.web.view.SuspensionView;
import org.letsemploy.ojobpub_publisher.web.view.UserRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everyone who has signed in, for platform staff (spec 7.20) - where viewing the
 * application as one of them starts (spec 2.9), and where an account is
 * suspended and reinstated (spec 2.11). Nobody else learns the list exists: a
 * non-admin gets 404 (spec 2.4).
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    static final int PAGE_SIZE = 20;
    /** The audit log's detail column holds 255; a reason is a sentence, not a report. */
    static final int MAX_REASON = 200;

    private final UserRepo userRepo;
    private final MembershipService membershipService;
    private final AuditLog auditLog;
    private final InputValidator inputs;
    private final PictureService pictures;

    public UserService(UserRepo userRepo, MembershipService membershipService, AuditLog auditLog,
                       InputValidator inputs, PictureService pictures) {
        this.userRepo = userRepo;
        this.membershipService = membershipService;
        this.auditLog = auditLog;
        this.inputs = inputs;
        this.pictures = pictures;
    }

    @Transactional(readOnly = true)
    public PageView<UserRow> page(String q, boolean suspendedOnly, int page, Actor actor) {
        requireAdmin(actor);
        Page<UserEntity> users = userRepo.search(q, suspendedOnly,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("displayName")));
        // One count query for the page, not one per row.
        List<UUID> ids = users.getContent().stream().map(UserEntity::getId).toList();
        Map<UUID, Long> employers = membershipService.countsByUser(ids);
        Map<UUID, String> pictureUrls = pictures.urlsFor(ids);
        List<UserRow> rows = users.getContent().stream()
                .map(u -> new UserRow(u.getId().toString(), u.getLabel(),
                        Avatar.of(pictureUrls.get(u.getId()), u.getLabel()), u.getEmail(), provider(u.getIssuer()),
                        u.getRole() == UserEntity.Role.ADMIN, employers.getOrDefault(u.getId(), 0L),
                        u.getId().equals(actor.getId()) ? "self"
                                : u.getRole() == UserEntity.Role.ADMIN ? "admin" : null,
                        u.isSuspended() ? Views.timestamp(u.getSuspendedAt()) : null))
                .toList();
        List<String> params = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            params.add("q=" + URLEncoder.encode(q.trim(), UTF_8));
        }
        if (suspendedOnly) {
            params.add("suspended=true");
        }
        String base = params.isEmpty() ? "/users" : "/users?" + String.join("&", params);
        return new PageView<>(rows, users.getNumber(), PAGE_SIZE, users.getTotalElements(), base);
    }

    /**
     * What suspending this account would leave behind (spec 2.11): the employers
     * it is the last active owner of. Named on the confirmation, never a reason
     * to refuse - suspension is for when an account must stop now, and an admin
     * can still act on those employers.
     */
    @Transactional(readOnly = true)
    public SuspensionView suspension(UUID userId, Actor actor) {
        requireAdmin(actor);
        UserEntity target = find(userId);
        refuseIfProtected(target, actor);
        return new SuspensionView(target.getId().toString(), target.getLabel(), target.getEmail(),
                membershipService.soleOwnershipsOf(target.getId()).stream().map(Employer::getName).toList());
    }

    /**
     * Suspend an account (spec 2.11): it cannot sign in, and an open session ends
     * at its next request. Memberships, roles and history all stay.
     *
     * <p>Not oneself, and not an admin. An admin made by configuration would be
     * re-made at every request, and one stored by hand is a peer: either way the
     * operator removes the role first. The reason, if given, goes to the audit
     * log, which the person reads once reinstated (spec 3.12).
     */
    @Transactional
    public void suspend(UUID userId, String reason, Actor actor) {
        requireAdmin(actor);
        UserEntity target = find(userId);
        if (target.isSuspended()) {
            return;
        }
        refuseIfProtected(target, actor);
        String why = reason == null || reason.isBlank() ? null : reason.strip();
        inputs.check(new SuspensionInput(why));
        target.setSuspendedAt(Instant.now());
        userRepo.save(target);
        auditLog.record(AuditEvent.of(AuditAction.ACCOUNT_SUSPENDED, actor).about(target)
                .target(target.getId(), target.getLabel()).detail(why));
        log.info("Admin {} suspended the account of user {}", actor.getId(), target.getId());
    }

    /** Lift a suspension: the account signs in again, with everything it had. */
    @Transactional
    public void reinstate(UUID userId, Actor actor) {
        requireAdmin(actor);
        UserEntity target = find(userId);
        if (!target.isSuspended()) {
            return;
        }
        target.setSuspendedAt(null);
        userRepo.save(target);
        auditLog.record(AuditEvent.of(AuditAction.ACCOUNT_REINSTATED, actor).about(target)
                .target(target.getId(), target.getLabel()));
        log.info("Admin {} reinstated the account of user {}", actor.getId(), target.getId());
    }

    /** Nobody else learns these screens or accounts exist (spec 2.4). */
    private static void requireAdmin(Actor actor) {
        if (actor.isToken() || !actor.isAdmin()) {
            throw new NotFoundException("Not found.");
        }
    }

    private UserEntity find(UUID userId) {
        return userRepo.findById(userId).orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    /** The same two exceptions as viewing as someone (spec 2.9): oneself, and an admin. */
    private static void refuseIfProtected(UserEntity target, Actor actor) {
        if (target.getId().equals(actor.getId())) {
            throw new ValidationFailure("self", "You cannot suspend your own account.");
        }
        if (target.getRole() == UserEntity.Role.ADMIN) {
            throw new ValidationFailure("admin", "An admin cannot be suspended. Remove the admin role first.");
        }
    }

    /**
     * "accounts.google.com" rather than the full issuer; "email and password" for a
     * local account (spec 2.12); "development" for the seed.
     */
    static String provider(String issuer) {
        if (org.letsemploy.ojobpub_publisher.account.LocalAccount.ISSUER.equals(issuer)) {
            return "email and password";
        }
        try {
            String host = URI.create(issuer).getHost();
            return host != null ? host : "development";
        } catch (IllegalArgumentException notAUri) {
            return "development";
        }
    }
}
