package org.letsemploy.ojobpub_publisher.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.ServiceTokenAuthFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The transport rules of spec 11.6, applied before a query is parsed: POST only,
 * a bounded body, and a request budget per token.
 *
 * <p>Runs after {@link ServiceTokenAuthFilter}, because the budget is per token
 * and an unauthenticated request has none to spend.
 *
 * <p>Not a bean: a {@code Filter} bean is registered by Boot against every
 * request, which would apply the API's rules to the whole back-office. It is
 * constructed by the security chain that wants it and nothing else.
 */
@RequiredArgsConstructor
public class ApiTransportFilter extends OncePerRequestFilter {

    private final ApiRateLimiter rateLimiter;

    /** 256 KB is far more than any documented query, and far less than a denial of service. */
    private final long maxRequestBytes;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // No GET execution: a query must not be triggerable by a link or cached
        // by an intermediary (spec 11.2).
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            refuse(response, 405, "METHOD_NOT_ALLOWED", "The management API accepts POST only.");
            return;
        }
        if (request.getContentLengthLong() > maxRequestBytes) {
            refuse(response, 413, "REQUEST_TOO_LARGE",
                    "The request body exceeds " + maxRequestBytes + " bytes.");
            return;
        }

        Object actor = request.getAttribute(ServiceTokenAuthFilter.ACTOR_ATTRIBUTE);
        if (actor instanceof Actor a && a.getId() != null) {
            ApiRateLimiter.Decision decision = rateLimiter.check(a.getId());
            // The budget is published on every answer, spent or not, so a client
            // can pace itself rather than discover the ceiling by hitting it.
            response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
            response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetEpochSecond()));
            if (!decision.allowed()) {
                response.setHeader("Retry-After", String.valueOf(Math.max(
                        decision.resetEpochSecond() - java.time.Instant.now().getEpochSecond(), 1)));
                refuse(response, 429, "RATE_LIMITED",
                        "Too many requests. The budget resets shortly.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** Shaped like a GraphQL response, so a client parses one thing (spec 11.4). */
    private void refuse(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"errors\":[{\"message\":\"" + message
                + "\",\"extensions\":{\"code\":\"" + code + "\"}}]}");
    }
}
