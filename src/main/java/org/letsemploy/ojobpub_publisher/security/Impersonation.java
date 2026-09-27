package org.letsemploy.ojobpub_publisher.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Which user an admin is viewing the application as, if any (spec 2.9) - kept in
 * the session, server-side, like the active employer.
 *
 * <p>Read with {@code getSession(false)}, deliberately not through a
 * session-scoped bean: a request without a session - the stateless API, where a
 * service token acts - must never have one created for it, and never finds a
 * state to act on.
 */
public final class Impersonation {

    private static final String ATTRIBUTE = Impersonation.class.getName();

    /** The admin who started it, and the user being viewed. */
    public record State(UUID adminId, UUID targetId) implements Serializable {
    }

    private Impersonation() {
    }

    public static Optional<State> current() {
        HttpSession session = session(false);
        return session == null ? Optional.empty()
                : Optional.ofNullable((State) session.getAttribute(ATTRIBUTE));
    }

    static void start(UUID adminId, UUID targetId) {
        HttpSession session = session(true);
        if (session != null) {
            session.setAttribute(ATTRIBUTE, new State(adminId, targetId));
        }
    }

    static void clear() {
        HttpSession session = session(false);
        if (session != null) {
            session.removeAttribute(ATTRIBUTE);
        }
    }

    private static HttpSession session(boolean create) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getSession(create);
    }
}
