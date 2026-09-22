package org.letsemploy.ojobpub_publisher.web;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.springframework.stereotype.Component;

/**
 * Resolves which employers the current screen covers: the active employer if one
 * is chosen, otherwise every employer the user may see (spec 2.5).
 */
@Component
@RequiredArgsConstructor
public class Scope {

    private final CurrentUserService currentUserService;
    private final EmployerService employerService;
    private final EmployerContext employerContext;

    public Actor user() {
        return currentUserService.current();
    }

    public List<Employer> employers() {
        Actor user = user();
        List<Employer> visible = employerService.visibleTo(user);
        UUID active = employerContext.getActiveEmployerId();
        if (active == null) {
            return visible;
        }
        return visible.stream().filter(e -> e.getId().equals(active)).toList();
    }

    public List<UUID> employerIds() {
        return employers().stream().map(Employer::getId).toList();
    }

    /** The employer a newly created job or feed belongs to. */
    public Employer requireActiveEmployer() {
        List<Employer> employers = employers();
        if (employers.isEmpty()) {
            throw new NotFoundException("No employer selected.");
        }
        return employers.get(0);
    }

    public boolean hasSingleEmployer() {
        return employers().size() == 1;
    }
}
