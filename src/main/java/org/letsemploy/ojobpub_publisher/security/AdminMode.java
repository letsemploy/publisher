package org.letsemploy.ojobpub_publisher.security;

import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Whether an admin has switched to admin mode in this session (spec 2.10) - kept
 * in the session, server-side, like the active employer and a view-as.
 *
 * <p>Read with {@code getSession(false)}, never through a session-scoped bean,
 * for the reason {@link Impersonation} gives: the stateless API must never have
 * a session created for it. Holds the user it was chosen by, and counts for that
 * user only. The choice is stored either way, so leaving is remembered even
 * where sessions start in admin mode.
 */
public final class AdminMode {

    private static final String ATTRIBUTE = AdminMode.class.getName();

    record State(UUID userId, boolean on) implements Serializable {
    }

    private AdminMode() {
    }

    /** What this user chose in this session, if anything. */
    static Optional<Boolean> chosenBy(UUID userId) {
        HttpSession session = session(false);
        return Optional.ofNullable(session == null ? null : (State) session.getAttribute(ATTRIBUTE))
                .filter(state -> state.userId().equals(userId))
                .map(State::on);
    }

    static void choose(UUID userId, boolean on) {
        HttpSession session = session(true);
        if (session != null) {
            session.setAttribute(ATTRIBUTE, new State(userId, on));
        }
    }

    private static HttpSession session(boolean create) {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getSession(create)
                : null;
    }
}
