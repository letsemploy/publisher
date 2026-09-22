package org.letsemploy.ojobpub_publisher.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.token.ServiceTokenService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the management API with a bearer service token (spec 2.8, 11.2).
 *
 * <p>A missing or unusable token answers 401 and never redirects: an integration
 * receiving a login page instead of JSON fails in a way that is tedious to
 * diagnose.
 *
 * <p>Not a bean: Boot registers every {@code Filter} bean against every request,
 * which would demand a bearer token on the back-office too. The management
 * chain constructs it.
 */
@RequiredArgsConstructor
public class ServiceTokenAuthFilter extends OncePerRequestFilter {

    public static final String ACTOR_ATTRIBUTE = "ojobpub.actor";

    private final ServiceTokenService serviceTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            unauthorized(response, "A bearer service token is required.");
            return;
        }

        Optional<Actor> actor = serviceTokenService.authenticate(header.substring(7).trim());
        if (actor.isEmpty()) {
            // Unknown, malformed and revoked are one answer: a caller learns only
            // that this credential does not work.
            unauthorized(response, "That token is not valid.");
            return;
        }

        request.setAttribute(ACTOR_ATTRIBUTE, actor.get());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.get(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_SERVICE_TOKEN"))));
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"errors\":[{\"message\":\"" + message
                        + "\",\"extensions\":{\"code\":\"UNAUTHENTICATED\"}}]}");
    }
}
