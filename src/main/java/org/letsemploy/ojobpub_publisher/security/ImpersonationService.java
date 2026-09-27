package org.letsemploy.ojobpub_publisher.security;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.web.EmployerContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starting and stopping a view of the application as another user (spec 2.9).
 *
 * <p>Only a platform admin, acting as themselves, may start one, and only on a
 * user who is neither an admin nor themselves. Anyone else is told the user does
 * not exist (spec 2.4). The view is read-only; {@link ImpersonationGuard} refuses
 * every change while it lasts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImpersonationService {

    private final CurrentUserService currentUserService;
    private final UserRepo userRepo;
    private final EmployerContext employerContext;

    @Transactional(readOnly = true)
    public UserEntity start(UUID targetId) {
        Actor admin = currentUserService.realActor();
        if (admin.isToken() || !admin.isAdmin() || targetId.equals(admin.getId())) {
            throw new NotFoundException("User not found: " + targetId);
        }
        UserEntity target = userRepo.findById(targetId)
                .filter(user -> user.getRole() != UserEntity.Role.ADMIN)
                .orElseThrow(() -> new NotFoundException("User not found: " + targetId));
        Impersonation.start(admin.getId(), target.getId());
        // The admin's active employer may be one the user cannot see; start from
        // the user's own default instead (spec 2.5).
        employerContext.setActiveEmployerId(null);
        log.info("Admin {} started viewing as user {}", admin.getId(), target.getId());
        return target;
    }

    public void stop() {
        Impersonation.current().ifPresent(state ->
                log.info("Admin {} stopped viewing as user {}", state.adminId(), state.targetId()));
        Impersonation.clear();
        employerContext.setActiveEmployerId(null);
    }
}
