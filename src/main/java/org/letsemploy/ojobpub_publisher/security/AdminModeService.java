package org.letsemploy.ojobpub_publisher.security;

import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditLog;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.web.EmployerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Switching admin mode on and off (spec 2.10). Only a person whose stored role is
 * admin may, acting as themselves; anyone else is told there is nothing here
 * (spec 2.4). Each switch is in the admin's own audit log (spec 3.12).
 */
@Service
public class AdminModeService {

    private static final Logger log = LoggerFactory.getLogger(AdminModeService.class);

    private final CurrentUserService currentUserService;
    private final UserRepo userRepo;
    private final EmployerContext employerContext;
    private final AuditLog auditLog;

    public AdminModeService(CurrentUserService currentUserService,
                            UserRepo userRepo,
                            EmployerContext employerContext,
                            AuditLog auditLog) {
        this.currentUserService = currentUserService;
        this.userRepo = userRepo;
        this.employerContext = employerContext;
        this.auditLog = auditLog;
    }

    @Transactional
    public void enter() {
        choose(true);
    }

    @Transactional
    public void leave() {
        choose(false);
    }

    private void choose(boolean on) {
        if (!currentUserService.canSwitchAdminMode()) {
            throw new NotFoundException("Not found.");
        }
        Actor before = currentUserService.realActor();
        if (before.isAdmin() == on) {
            return;
        }
        UserEntity admin = userRepo.findById(before.getId())
                .orElseThrow(() -> new NotFoundException("Not found."));
        AdminMode.choose(admin.getId(), on);
        // The employer chosen in one mode may be one the other cannot see (spec 2.5).
        employerContext.setActiveEmployerId(null);
        // Recorded as the person, whichever mode they are entering.
        auditLog.record(AuditEvent.of(on ? AuditAction.ADMIN_MODE_ENTERED : AuditAction.ADMIN_MODE_LEFT, before)
                .about(admin).target(admin.getId(), admin.getLabel()));
        log.info("User {} {} admin mode", admin.getId(), on ? "entered" : "left");
    }
}
