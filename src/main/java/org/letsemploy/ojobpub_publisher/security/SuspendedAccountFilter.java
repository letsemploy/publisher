package org.letsemploy.ojobpub_publisher.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Signs a suspended account out, on its first request after signing in and on
 * the first request of a session that was open when it was suspended (spec 2.11).
 * The sign-in page then says why.
 *
 * <p>Checked on every request rather than only at sign-in: a session outlives
 * the sign-in that created it by hours, and a suspension is meant to take
 * effect now. It costs one lookup by (issuer, subject), which {@code signIn} was
 * making on the same request anyway.
 *
 * <p>Not a bean, like {@link ServiceTokenAuthFilter}: Boot would register a
 * {@code Filter} bean against every request. The OIDC chain constructs it.
 */
@RequiredArgsConstructor
@Slf4j
class SuspendedAccountFilter extends OncePerRequestFilter {

    static final String TARGET = "/login?suspended";

    private final CurrentUserService currentUserService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!currentUserService.isSuspended()) {
            chain.doFilter(request, response);
            return;
        }
        log.info("Signing out a suspended account");
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
        String target = request.getContextPath() + TARGET;
        // An htmx request would swap the sign-in page into the middle of a screen;
        // HX-Redirect makes it a whole-page navigation instead.
        if (request.getHeader("HX-Request") != null) {
            response.setHeader("HX-Redirect", target);
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } else {
            response.sendRedirect(target);
        }
    }
}
