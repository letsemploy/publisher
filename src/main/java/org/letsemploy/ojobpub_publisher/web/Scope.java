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
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Resolves the employer the current screen covers: always exactly one, the
 * active employer (spec 2.5), and none only for a user who belongs to none.
 */
@Component
public class Scope {

    private static final String VISIBLE = Scope.class.getName() + ".VISIBLE";

    /** The employers one actor may see, read once per request for that actor. */
    private record Visible(Actor actor, List<Employer> employers) {
    }

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
        return employerContext.resolve(visibleEmployers());
    }

    /**
     * Every employer the actor may see, by name: the switcher's list, and what the
     * active one is chosen from. Kept for the request while the actor is the same
     * one - the same instance, which {@link CurrentUserService} keeps for a GET -
     * so the screen and the shell read it once between them.
     */
    public List<Employer> visibleEmployers() {
        Actor actor = user();
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null && request.getAttribute(VISIBLE, RequestAttributes.SCOPE_REQUEST) instanceof Visible visible
                && visible.actor() == actor) {
            return visible.employers();
        }
        List<Employer> employers = employerService.visibleTo(actor);
        if (request != null) {
            request.setAttribute(VISIBLE, new Visible(actor, employers), RequestAttributes.SCOPE_REQUEST);
        }
        return employers;
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
