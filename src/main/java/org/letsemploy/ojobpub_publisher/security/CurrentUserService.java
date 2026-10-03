package org.letsemploy.ojobpub_publisher.security;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.account.LocalAccount;
import org.letsemploy.ojobpub_publisher.account.LocalAccountRepo;
import org.letsemploy.ojobpub_publisher.account.LocalUser;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditLog;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the current {@link Actor}: creating the local account on first OIDC
 * sign-in, and keeping its name and email in step with the identity provider on
 * every one after (spec 2.2).
 */
@Service
public class CurrentUserService {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserService.class);

    /** The seeded development administrator (spec 2.3). */
    public static final String DEV_ISSUER = "dev";
    public static final String DEV_SUBJECT = "dev@localhost";

    private final UserRepo userRepo;
    private final MembershipService membershipService;
    private final AdminPolicy adminPolicy;
    private final Environment environment;
    private final AuditLog auditLog;
    private final LocalAccountRepo localAccounts;

    public CurrentUserService(UserRepo userRepo,
                              MembershipService membershipService,
                              AdminPolicy adminPolicy,
                              Environment environment,
                              AuditLog auditLog,
                              LocalAccountRepo localAccounts,
                              @Value("${app.oidc.require-verified-email:true}") boolean requireVerifiedEmail,
                              @Value("${app.admin.start-in-admin-mode:false}") boolean startInAdminMode) {
        this.userRepo = userRepo;
        this.membershipService = membershipService;
        this.adminPolicy = adminPolicy;
        this.environment = environment;
        this.auditLog = auditLog;
        this.localAccounts = localAccounts;
        this.requireVerifiedEmail = requireVerifiedEmail;
        this.startInAdminMode = startInAdminMode;
    }

    /**
     * Keep an email only when the provider says it is verified (spec 2.2). An
     * invitation goes to whichever account holds the address (spec 2.6), so an
     * unverified one would let anyone who can type an address into their profile
     * collect another person's invitations. Off only for a provider that never
     * sends {@code email_verified} at all.
     */
    private final boolean requireVerifiedEmail;

    /**
     * Whether an admin's session starts in admin mode (spec 2.10). Off by default:
     * an admin works as an ordinary member until they switch up. The test profile
     * turns it on, so tests written before admin mode still act with full reach.
     */
    private final boolean startInAdminMode;

    public boolean isDevMode() {
        return List.of(environment.getActiveProfiles()).contains("dev");
    }

    /**
     * Whoever the application acts for on this request: the signed-in person, or -
     * while an admin views the application as someone (spec 2.9) - that someone.
     */
    @Transactional
    public Actor current() {
        Actor real = realActor();
        return Impersonation.current().map(state -> viewAs(state, real)).orElse(real);
    }

    /**
     * The person actually signed in, ignoring any impersonation: who may start or
     * stop one, and who is answerable for it (spec 2.9).
     */
    @Transactional
    public Actor realActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Development mode: no identity provider, but a real user record all the same -
        // anything keyed on user identity is otherwise unreachable locally (spec 2.3).
        Identity identity = auth == null ? null : identityOf(auth.getPrincipal());
        if (isDevMode() && identity == null) {
            return toAppUser(devUser());
        }
        if (identity == null) {
            return Actor.anonymous();
        }
        // A suspended account is nobody (spec 2.11). SuspendedAccountFilter ends
        // its session before a handler runs; this is what holds if it did not.
        UserEntity user = signIn(identity);
        return user.isSuspended() ? Actor.anonymous() : toAppUser(user);
    }

    /**
     * Whether the person signed in on this request holds a suspended account
     * (spec 2.11) - read by {@link SuspendedAccountFilter}, which signs them out.
     * False for anyone not signed in, and for the development bypass, whose seeded
     * admin cannot be suspended.
     */
    @Transactional(readOnly = true)
    public boolean isSuspended() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Identity identity = auth == null ? null : identityOf(auth.getPrincipal());
        return identity != null && userRepo.findByIssuerAndSubject(identity.issuer(), identity.subject())
                .map(UserEntity::isSuspended).orElse(false);
    }

    /**
     * Where a session that must end now is sent, or empty while it may go on -
     * read by {@link SuspendedAccountFilter}. A suspended account (spec 2.11), and
     * a local account whose password changed after this session signed in (spec
     * 2.12): every other session of it ends at its next request.
     */
    @Transactional(readOnly = true)
    public Optional<String> endOfSession() {
        if (isSuspended()) {
            return Optional.of(SuspendedAccountFilter.SUSPENDED);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LocalUser local) {
            boolean superseded = localAccounts.findById(local.getId())
                    .map(account -> account.getPasswordChangedAt() == null
                            || account.getPasswordChangedAt().isAfter(local.getSignedInAt()))
                    .orElse(true);
            if (superseded) {
                return Optional.of(SuspendedAccountFilter.EXPIRED);
            }
        }
        return Optional.empty();
    }

    /**
     * The user being viewed, with their own memberships and never admin standing.
     * Re-checked on every request: the viewer must still be the admin who started
     * it - demoted by the admin rules since (spec 2.2), it ends at once - and the
     * target must still exist and not be an admin. Otherwise it ends.
     *
     * <p>signIn() is deliberately not called for the target: it refreshes name,
     * email and role from the signed-in token, which is the admin's.
     */
    private Actor viewAs(Impersonation.State state, Actor real) {
        if (real.isToken() || !real.isAdmin() || !state.adminId().equals(real.getId())) {
            Impersonation.clear();
            return real;
        }
        return userRepo.findById(state.targetId())
                .filter(target -> target.getRole() != UserEntity.Role.ADMIN)
                .map(this::toAppUser)
                .orElseGet(() -> {
                    Impersonation.clear();
                    return real;
                });
    }

    /** The user being viewed, for the banner - or empty when nobody is (spec 2.9). */
    @Transactional(readOnly = true)
    public Optional<UserEntity> viewedUser() {
        Actor actor = current();
        return Impersonation.current().isPresent() && !actor.isAnonymous()
                ? userRepo.findById(actor.getId()).filter(u -> Impersonation.current()
                        .map(state -> state.targetId().equals(u.getId())).orElse(false))
                : Optional.empty();
    }

    public boolean isImpersonating() {
        return viewedUser().isPresent();
    }

    /**
     * May the signed-in person switch admin mode on or off (spec 2.10)? Only a
     * person whose stored role is admin, acting as themselves - not a token, and
     * not while viewing as someone, whose view it is not.
     */
    @Transactional(readOnly = true)
    public boolean canSwitchAdminMode() {
        Actor real = realActor();
        return !real.isToken() && !real.isAnonymous() && Impersonation.current().isEmpty()
                && userRepo.findById(real.getId()).map(u -> u.getRole() == UserEntity.Role.ADMIN).orElse(false);
    }

    /** Seeded by data.sql, but provisioned on demand so dev works on an empty database. */
    private UserEntity devUser() {
        return userRepo.findByIssuerAndSubject(DEV_ISSUER, DEV_SUBJECT).orElseGet(() -> {
            UserEntity user = new UserEntity();
            user.setIssuer(DEV_ISSUER);
            user.setSubject(DEV_SUBJECT);
            user.setEmail(DEV_SUBJECT);
            user.setDisplayName("dev@localhost");
            user.setRole(UserEntity.Role.ADMIN);
            return userRepo.save(user);
        });
    }

    private Actor toAppUser(UserEntity user) {
        String name = Optional.ofNullable(user.getDisplayName())
                .orElse(Optional.ofNullable(user.getEmail()).orElse(user.getSubject()));
        // Suspended memberships are left out, so every check downstream sees a
        // suspended member exactly as it sees a non-member (spec 2.7).
        Map<UUID, MembershipRole> memberships = membershipService.activeRolesOf(user.getId());
        // The one place admin standing is granted, so switching admin mode off
        // takes it away everywhere at once (spec 2.10). It counts only while the
        // stored role is admin: a demotion ends it with no further check.
        boolean admin = user.getRole() == UserEntity.Role.ADMIN
                && AdminMode.chosenBy(user.getId()).orElse(startInAdminMode);
        return Actor.user(user.getId(), name, user.getEmail(), admin, memberships);
    }

    /**
     * Who a signed-in principal is, whichever way they signed in (spec 2.2): the
     * issuer and subject that identify them, the name to show, and an email only
     * if it may be trusted - or null when the principal is nobody we recognise.
     */
    private record Identity(String issuer, String subject, String displayName, String email,
                            boolean emailVerified, Map<String, Object> claims) {
    }

    private Identity identityOf(Object principal) {
        if (principal instanceof OidcUser oidc) {
            // getAttributes() merges ID-token and user-info claims: groups often
            // arrive through user-info, and the admin mapping reads them (spec 2.2).
            return new Identity(String.valueOf(oidc.getIssuer()), oidc.getSubject(),
                    displayName(oidc), trustedEmail(oidc),
                    Boolean.TRUE.equals(oidc.getEmailVerified()), oidc.getAttributes());
        }
        // A local account: its address was confirmed before it could have a
        // password, so it is verified by construction (spec 2.12).
        if (principal instanceof LocalUser local) {
            // The name is read from the account, not the session: it is the
            // person's to change (spec 7.26), and every other open session would
            // otherwise write the old one back at its next request.
            String name = localAccounts.findById(local.getId())
                    .map(LocalAccount::getDisplayName).orElse(local.getDisplayName());
            return new Identity(LocalAccount.ISSUER, local.getId().toString(), name,
                    local.getEmail(), true, Map.of());
        }
        // GitHub: OAuth 2.0 without OpenID Connect, identified by GitHubUserService.
        if (principal instanceof GitHubUser github) {
            String email = github.getVerifiedEmail() != null || requireVerifiedEmail
                    ? github.getVerifiedEmail()
                    : github.getPublicEmail();
            return new Identity(github.getIssuer(), github.getSubject(), github.getDisplayName(), email,
                    github.getVerifiedEmail() != null, github.getAttributes());
        }
        return null;
    }

    /**
     * The local account for this identity: found by the stable (issuer, subject)
     * pair, never by email, and created on first sign-in (spec 2.2).
     *
     * <p>A new account has the User platform role and no memberships, so it sees
     * an empty state until it creates an employer or is invited. Name and email
     * are the provider's to change, so they are refreshed from it - and written
     * only when they differ, since this runs on every request.
     */
    private UserEntity signIn(Identity identity) {
        UserEntity user = userRepo.findByIssuerAndSubject(identity.issuer(), identity.subject())
                .orElseGet(() -> {
                    UserEntity created = new UserEntity();
                    created.setIssuer(identity.issuer());
                    created.setSubject(identity.subject());
                    created.setRole(UserEntity.Role.USER);
                    log.info("New account for {} at {}", identity.subject(), identity.issuer());
                    return created;
                });
        // Left exactly as it was: not refreshed from the token, and its role not
        // re-decided, while nobody may act through it (spec 2.11).
        if (user.isSuspended()) {
            return user;
        }
        // Admin as configured, or the stored role when admins are not managed here.
        UserEntity.Role role = adminPolicy.isAdmin(identity.issuer(), identity.subject(),
                        identity.email(), identity.emailVerified(), identity.claims())
                .map(admin -> admin ? UserEntity.Role.ADMIN : UserEntity.Role.USER)
                .orElse(user.getRole());
        if (user.getId() != null
                && Objects.equals(user.getEmail(), identity.email())
                && Objects.equals(user.getDisplayName(), identity.displayName())
                && user.getRole() == role) {
            return user;
        }
        if (user.getId() != null && user.getRole() != role) {
            // Names the account, never the rule that matched: a claim value is the
            // provider's data and has no business in a log line.
            log.info("User {} ({} at {}) {}", user.getId(), identity.subject(), identity.issuer(),
                    role == UserEntity.Role.ADMIN ? "granted admin" : "is no longer admin");
        } else if (user.getId() == null && role == UserEntity.Role.ADMIN) {
            log.info("New account for {} at {} granted admin", identity.subject(), identity.issuer());
        }
        boolean created = user.getId() == null;
        UserEntity.Role before = user.getRole();
        user.setEmail(identity.email());
        user.setDisplayName(identity.displayName());
        user.setRole(role);
        UserEntity saved = userRepo.save(user);
        // Only what changes an account's standing; a refreshed name is not an
        // event, and signing in is recorded by the identity provider (spec 3.12).
        if (created) {
            auditLog.record(AuditEvent.of(AuditAction.ACCOUNT_CREATED, null).about(saved)
                    .target(saved.getId(), saved.getLabel()));
        }
        if (created ? role == UserEntity.Role.ADMIN : before != role) {
            // By the application, from its configured rules - not by any person.
            auditLog.record(AuditEvent.of(role == UserEntity.Role.ADMIN
                            ? AuditAction.ADMIN_GRANTED : AuditAction.ADMIN_REVOKED, null)
                    .about(saved).target(saved.getId(), saved.getLabel()));
        }
        return saved;
    }

    private String trustedEmail(OidcUser oidc) {
        if (oidc.getEmail() == null) {
            return null;
        }
        if (requireVerifiedEmail && !Boolean.TRUE.equals(oidc.getEmailVerified())) {
            log.debug("Not storing the unverified email of {}", oidc.getSubject());
            return null;
        }
        return oidc.getEmail();
    }

    private static String displayName(OidcUser oidc) {
        if (oidc.getFullName() != null && !oidc.getFullName().isBlank()) {
            return oidc.getFullName();
        }
        if (oidc.getPreferredUsername() != null && !oidc.getPreferredUsername().isBlank()) {
            return oidc.getPreferredUsername();
        }
        return oidc.getEmail();
    }
}
