package org.letsemploy.ojobpub_publisher.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts events per key in a fixed window, in memory and per instance, like the
 * API's rate limit (spec 11.6, 12). Enough to make guessing passwords, and
 * mailing strangers, slow (spec 2.12); a shared counter is the open question of
 * spec 12 for both.
 */
final class Throttle {

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    Throttle(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    /** Whether the key has used up its window. 0 means unlimited, as for the quotas. */
    boolean exhausted(String key) {
        if (limit == 0) {
            return false;
        }
        Window current = windows.get(key);
        return current != null && !current.over(clock.instant()) && current.count() >= limit;
    }

    void record(String key) {
        Instant now = clock.instant();
        // Drop windows that ended, so the map holds only the recently active.
        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> w.over(now));
        }
        windows.compute(key, (k, w) -> w == null || w.over(now)
                ? new Window(now.plus(window), 1)
                : new Window(w.endsAt(), w.count() + 1));
    }

    private record Window(Instant endsAt, int count) {
        boolean over(Instant now) {
            return !now.isBefore(endsAt);
        }
    }
}
