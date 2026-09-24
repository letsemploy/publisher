package org.letsemploy.ojobpub_publisher.security;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
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
@RequiredArgsConstructor
@Slf4j
public class CurrentUserService {

    /** The seeded development administrator (spec 2.3). */
    public static final String DEV_ISSUER = "dev";
    public static final String DEV_SUBJECT = "dev@localhost";

    private final UserRepo userRepo;
    private final MembershipService membershipService;
    private final Environment environment;

    /**
     * Keep an email only when the provider says it is verified (spec 2.2). An
     * invitation goes to whichever account holds the address (spec 2.6), so an
     * unverified one would let anyone who can type an address into their profile
     * collect another person's invitations. Off only for a provider that never
     * sends {@code email_verified} at all.
     */
    @Value("${app.oidc.require-verified-email:true}")
    private boolean requireVerifiedEmail;

    public boolean isDevMode() {
        return List.of(environment.getActiveProfiles()).contains("dev");
    }

    @Transactional
    public Actor current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Development mode: no identity provider, but a real user record all the same -
        // anything keyed on user identity is otherwise unreachable locally (spec 2.3).
        if (isDevMode() && (auth == null || !(auth.getPrincipal() instanceof OidcUser))) {
            return toAppUser(devUser());
        }

        if (auth != null && auth.getPrincipal() instanceof OidcUser oidc) {
            return toAppUser(signIn(oidc));
        }
        return Actor.anonymous();
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
        return Actor.user(user.getId(), name, user.getEmail(),
                user.getRole() == UserEntity.Role.ADMIN, memberships);
    }

    /**
     * The local account for this identity: found by the stable (issuer, subject)
     * pair, never by email, and created on first sign-in (spec 2.2).
     *
     * <p>A new account has the User platform role and no memberships, so it sees
     * an empty state until it creates an employer or is invited. Name and email
     * are the provider's to change, so they are refreshed from the token - and
     * written only when they differ, since this runs on every request.
     */
    private UserEntity signIn(OidcUser oidc) {
        String issuer = String.valueOf(oidc.getIssuer());
        String email = trustedEmail(oidc);
        String name = displayName(oidc);
        UserEntity user = userRepo.findByIssuerAndSubject(issuer, oidc.getSubject())
                .orElseGet(() -> {
                    UserEntity created = new UserEntity();
                    created.setIssuer(issuer);
                    created.setSubject(oidc.getSubject());
                    created.setRole(UserEntity.Role.USER);
                    log.info("New account for {} at {}", oidc.getSubject(), issuer);
                    return created;
                });
        if (user.getId() != null
                && Objects.equals(user.getEmail(), email)
                && Objects.equals(user.getDisplayName(), name)) {
            return user;
        }
        user.setEmail(email);
        user.setDisplayName(name);
        return userRepo.save(user);
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
