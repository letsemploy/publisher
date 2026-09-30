package org.letsemploy.ojobpub_publisher.web;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.springframework.stereotype.Component;

/**
 * Resolves the employer the current screen covers: always exactly one, the
 * active employer (spec 2.5), and none only for a user who belongs to none.
 */
@Component
public class Scope {

    private final CurrentUserService currentUserService;
    private final EmployerService employerService;
    private final EmployerContext employerContext;

    public Scope(CurrentUserService currentUserService,
                 EmployerService employerService,
                 EmployerContext employerContext) {
        this.currentUserService = currentUserService;
        this.employerService = employerService;
        this.employerContext = employerContext;
    }

    public Actor user() {
        return currentUserService.current();
    }

    /**
     * The active employer: the one chosen while the user may still see it, else
     * the first they may see, by name (spec 2.5). Empty with no employers at all.
     */
    public Optional<Employer> activeEmployer() {
        return employerContext.resolve(employerService.visibleTo(user()));
    }

    /** The active employer as a list, for the queries that take several: one, or none. */
    public List<Employer> employers() {
        return activeEmployer().stream().toList();
    }

    public List<UUID> employerIds() {
        return employers().stream().map(Employer::getId).toList();
    }

    /** The employer a new record belongs to; 404 for a user who belongs to none. */
    public Employer requireActiveEmployer() {
        return activeEmployer().orElseThrow(() -> new NotFoundException("No employer selected."));
    }
}
