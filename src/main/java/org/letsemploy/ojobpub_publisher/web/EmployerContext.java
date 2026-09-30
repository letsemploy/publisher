package org.letsemploy.ojobpub_publisher.web;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

/**
 * The active employer and theme (spec 2.5, 7.3).
 *
 * <p>{@code activeEmployerId} is a choice, and null means none made yet. Which
 * employer is active is always {@link #resolve}d against what the user may see.
 *
 * <p>Held server-side in the session, never in a client-controlled cookie: it is a
 * convenience, and authorization is always derived from membership instead.
 */
@Component
@SessionScope
@Getter
@Setter
public class EmployerContext implements Serializable {

    private UUID activeEmployerId;
    /** {@code auto} follows the operating system until the user chooses (spec 7.3). */
    private String theme = "auto";

    /**
     * The active employer among those visible, in order (spec 2.5): the chosen one
     * while it is still visible, else the first. The answer is kept as the choice,
     * so one left behind - a membership removed, an employer deleted - heals
     * itself. Empty only for a user who belongs to no employer.
     */
    public Optional<Employer> resolve(List<Employer> visible) {
        Optional<Employer> active = visible.stream()
                .filter(e -> e.getId().equals(activeEmployerId))
                .findFirst()
                .or(() -> visible.stream().findFirst());
        UUID resolved = active.map(Employer::getId).orElse(null);
        if (resolved != null && !resolved.equals(activeEmployerId)) {
            activeEmployerId = resolved;
        }
        return active;
    }
}
