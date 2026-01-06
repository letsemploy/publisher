package org.letsemploy.ojobpub_publisher.security;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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

    private final UserRepo userRepo;
    private final Environment environment;

    public boolean isDevMode() {
        return List.of(environment.getActiveProfiles()).contains("dev");
    }

    @Transactional
    public AppUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Development mode: a fixed synthetic administrator, no identity provider (spec 2.3).
        if (isDevMode() && (auth == null || !(auth.getPrincipal() instanceof OidcUser))) {
            return new AppUser("dev@localhost", true, List.of());
        }

        if (auth != null && auth.getPrincipal() instanceof OidcUser oidc) {
            UserEntity user = userRepo
                    .findByIssuerAndSubject(String.valueOf(oidc.getIssuer()), oidc.getSubject())
                    .orElseGet(() -> provision(oidc));
            String name = Optional.ofNullable(user.getDisplayName())
                    .orElse(Optional.ofNullable(user.getEmail()).orElse(user.getSubject()));
            return new AppUser(name, user.getRole() == UserEntity.Role.ADMIN,
                    List.copyOf(user.getEmployerIds()));
        }
        return new AppUser("anonymous", false, List.of());
    }

    /**
     * A newly provisioned user is an editor with no employer memberships: they can
     * log in and see an empty state until an administrator grants access (spec 2.2).
     */
    private UserEntity provision(OidcUser oidc) {
        UserEntity user = new UserEntity();
        user.setIssuer(String.valueOf(oidc.getIssuer()));
        user.setSubject(oidc.getSubject());
        user.setEmail(oidc.getEmail());
        user.setDisplayName(oidc.getFullName() != null ? oidc.getFullName() : oidc.getEmail());
        user.setRole(UserEntity.Role.EDITOR);
        return userRepo.save(user);
    }
}
