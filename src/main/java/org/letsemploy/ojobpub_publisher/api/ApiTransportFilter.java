package org.letsemploy.ojobpub_publisher.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
public class ApiTransportFilter extends OncePerRequestFilter {

    private final ApiRateLimiter rateLimiter;

    /** 256 KB is far more than any documented query, and far less than a denial of service. */
    private final long maxRequestBytes;

    public ApiTransportFilter(ApiRateLimiter rateLimiter, long maxRequestBytes) {
        this.rateLimiter = rateLimiter;
        this.maxRequestBytes = maxRequestBytes;
    }

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
            refuseTooLarge(response);
            return;
        }
        // A chunked body declares no length (-1) and would pass the check above, so
        // it is read here, at most one byte past the limit, and handed on from memory.
        if (request.getContentLengthLong() < 0) {
            byte[] body = request.getInputStream().readNBytes(Math.toIntExact(maxRequestBytes + 1));
            if (body.length > maxRequestBytes) {
                refuseTooLarge(response);
                return;
            }
            request = new BufferedBodyRequest(request, body);
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

    private void refuseTooLarge(HttpServletResponse response) throws IOException {
        refuse(response, 413, "REQUEST_TOO_LARGE",
                "The request body exceeds " + maxRequestBytes + " bytes.");
    }

    /** A request whose body has already been read, served again from memory. */
    private static final class BufferedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("The body is already in memory.");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(),
                    getCharacterEncoding() == null ? StandardCharsets.UTF_8
                            : Charset.forName(getCharacterEncoding())));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
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
