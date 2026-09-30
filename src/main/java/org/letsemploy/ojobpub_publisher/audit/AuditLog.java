package org.letsemploy.ojobpub_publisher.audit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit log (spec 3.12). Called by each service at the point where it
 * makes a change, never by a controller, so the API and the screens record the
 * same way.
 *
 * <p>Always inside the caller's transaction ({@link Propagation#MANDATORY}): a
 * change that rolls back leaves no record, and a refusal - which throws before
 * reaching here - is never recorded as something that happened.
 */
@Component
public class AuditLog {

    private final AuditRepo auditRepo;

    public AuditLog(AuditRepo auditRepo) {
        this.auditRepo = auditRepo;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEvent event) {
        auditRepo.save(event);
    }
}
