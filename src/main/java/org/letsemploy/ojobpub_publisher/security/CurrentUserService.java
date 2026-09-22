package org.letsemploy.ojobpub_publisher.security;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.membership.Membership;
import org.letsemploy.ojobpub_publisher.membership.MembershipRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves the current {@link AppUser}, provisioning on first OIDC login (spec 2.2). */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    /** The seeded development administrator (spec 2.3). */
    public static final String DEV_ISSUER = "dev";
    public static final String DEV_SUBJECT = "dev@localhost";

    private final UserRepo userRepo;
    private final MembershipRepo membershipRepo;
    private final Environment environment;

    public boolean isDevMode() {
        return List.of(environment.getActiveProfiles()).contains("dev");
    }

    @Transactional
    public AppUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Development mode: no identity provider, but a real user record all the same -
        // anything keyed on user identity is otherwise unreachable locally (spec 2.3).
        if (isDevMode() && (auth == null || !(auth.getPrincipal() instanceof OidcUser))) {
            return toAppUser(devUser());
        }

        if (auth != null && auth.getPrincipal() instanceof OidcUser oidc) {
            UserEntity user = userRepo
                    .findByIssuerAndSubject(String.valueOf(oidc.getIssuer()), oidc.getSubject())
                    .orElseGet(() -> provision(oidc));
            return toAppUser(user);
        }
        return new AppUser(null, "anonymous", null, false, Map.of());
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

    private AppUser toAppUser(UserEntity user) {
        String name = Optional.ofNullable(user.getDisplayName())
                .orElse(Optional.ofNullable(user.getEmail()).orElse(user.getSubject()));
        Map<UUID, MembershipRole> memberships = membershipRepo.findByUserId(user.getId()).stream()
                .collect(Collectors.toMap(m -> m.getEmployer().getId(), Membership::getRole,
                        (a, b) -> a));
        return new AppUser(user.getId(), name, user.getEmail(),
                user.getRole() == UserEntity.Role.ADMIN, memberships);
    }

    /**
     * A newly provisioned user is an editor with no employer memberships: they can
     * log in and see an empty state until someone invites them (spec 2.2).
     */
    private UserEntity provision(OidcUser oidc) {
        UserEntity user = new UserEntity();
        user.setIssuer(String.valueOf(oidc.getIssuer()));
        user.setSubject(oidc.getSubject());
        user.setEmail(oidc.getEmail());
        user.setDisplayName(oidc.getFullName() != null ? oidc.getFullName() : oidc.getEmail());
        user.setRole(UserEntity.Role.USER);
        return userRepo.save(user);
    }
}
