package org.letsemploy.ojobpub_publisher.api;

import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The actor behind the current API request, and its scope checks (spec 2.8).
 *
 * <p>A scope is a ceiling on top of the membership role, not a substitute for it:
 * the services still apply the role. This only refuses early, with a clearer
 * answer than "not found".
 */
@Component
public class ApiActor {

    public Actor current() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Actor actor) {
            return actor;
        }
        throw new ApiForbiddenException("No authenticated actor on this request.");
    }

    /** The employer is implied by the token and is never an argument (spec 11.2). */
    public java.util.UUID employerId() {
        Actor actor = current();
        return actor.getEmployerIds().stream().findFirst()
                .orElseThrow(() -> new NotFoundException("This token belongs to no employer."));
    }

    public Actor requireScope(TokenScope scope) {
        Actor actor = current();
        if (!actor.hasScope(scope)) {
            throw new ApiForbiddenException(
                    "This token lacks the " + scope.name().toLowerCase().replace('_', ':') + " scope.");
        }
        return actor;
    }
}
