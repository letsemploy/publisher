package io.letsemploy.publisher.service;

import io.letsemploy.publisher.domain.AppUser;
import io.letsemploy.publisher.repository.AppUserRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OidcUserProvisioningService {

    private final AppUserRepository userRepository;

    public OidcUserProvisioningService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void provision(OidcUser oidcUser) {
        userRepository.findByExternalSubject(oidcUser.getSubject())
                .ifPresentOrElse(existing -> {
                    existing.setDisplayName(oidcUser.getFullName() != null ? oidcUser.getFullName() : oidcUser.getPreferredUsername());
                    if (oidcUser.getEmail() != null) {
                        existing.setEmail(oidcUser.getEmail());
                    }
                    userRepository.save(existing);
                }, () -> {
                    AppUser user = new AppUser();
                    user.setExternalSubject(oidcUser.getSubject());
                    user.setDisplayName(oidcUser.getFullName() != null ? oidcUser.getFullName() : oidcUser.getPreferredUsername());
                    user.setEmail(oidcUser.getEmail() != null ? oidcUser.getEmail() : oidcUser.getSubject() + "@local.invalid");
                    userRepository.save(user);
                });
    }
}
