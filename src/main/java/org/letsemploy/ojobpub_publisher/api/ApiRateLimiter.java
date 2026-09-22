package org.letsemploy.ojobpub_publisher.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A request budget per token, so the API is bounded by construction rather than
 * by hope (spec 11.6).
 *
 * <p>A fixed window in memory, which is the honest shape for a single instance:
 * it is not shared between instances and it does not survive a restart, so
 * behind more than one replica the effective limit is the limit times the
 * replica count. That is a deliberate trade - a shared counter would mean a
 * round trip to Redis on every request for a limit that exists to stop runaway
 * scripts, not to meter a paid product. Recorded in the spec's open questions.
 */
@Component
public class ApiRateLimiter {

    /** One decision, so the headers and the refusal cannot disagree. */
    public record Decision(boolean allowed, int limit, int remaining, long resetEpochSecond) {
    }

    private record Window(long startEpochSecond, int used) {
    }

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowSeconds;

    public ApiRateLimiter(@Value("${app.api.rate-limit:600}") int limit,
                          @Value("${app.api.rate-limit-window:PT1M}") Duration window) {
        this.limit = limit;
        this.windowSeconds = Math.max(window.toSeconds(), 1);
    }

    public Decision check(UUID tokenId) {
        long now = Instant.now().getEpochSecond();
        Window updated = windows.compute(tokenId, (id, current) -> {
            if (current == null || now - current.startEpochSecond() >= windowSeconds) {
                return new Window(now, 1);
            }
            return new Window(current.startEpochSecond(), current.used() + 1);
        });
        long reset = updated.startEpochSecond() + windowSeconds;
        int remaining = Math.max(limit - updated.used(), 0);
        return new Decision(updated.used() <= limit, limit, remaining, reset);
    }
}
