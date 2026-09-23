package org.letsemploy.ojobpub_publisher.web;

import java.util.List;
import java.util.Optional;
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

    /**
     * The one employer the current screen is about, if there is one (spec 2.5).
     *
     * <p>Distinct from {@link #requireActiveEmployer()}, which picks a default to
     * create against. A screen that names a subject - People (spec 7.18) - must
     * not quietly pick the first of several: with "All employers" chosen, or with
     * no memberships at all, the honest answer is that there is no subject.
     */
    public Optional<Employer> activeEmployer() {
        UUID chosen = employerContext.getActiveEmployerId();
        List<Employer> visible = employerService.visibleTo(user());
        if (chosen != null) {
            return visible.stream().filter(e -> e.getId().equals(chosen)).findFirst();
        }
        // One employer is unambiguously the one being worked on, chosen or not.
        return visible.size() == 1 ? Optional.of(visible.get(0)) : Optional.empty();
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
