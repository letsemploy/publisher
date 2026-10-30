package org.letsemploy.ojobpub_publisher.account;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts events per key in a fixed window, in memory and per instance, like the
 * API's rate limit (spec 11.6, 12). Enough to make guessing passwords, and
 * mailing strangers, slow (spec 2.12); a shared counter is the open question of
 * spec 12 for both.
 *
 * <p>Bounded, since every key is chosen by whoever is asking: windows that ended
 * are swept at most once per {@link #SWEEP_EVERY}, so a flood costs one pass per
 * interval rather than one per request, and at {@code maxKeys} a new key is not
 * counted at all. It never takes the place of a running window: a flood could
 * otherwise push out the very key it is guessing at and start that key afresh.
 * What is not counted is {@link #uncounted}, and the callers treat it as worse
 * than counted, never better - a captcha for every try, or a refusal.
 */
final class Throttle {

    /** Far above what one instance sees from honest clients; about 20 MB of windows. */
    static final int MAX_KEYS = 100_000;
    static final Duration SWEEP_EVERY = Duration.ofSeconds(30);

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final int maxKeys;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private volatile Instant nextSweep = Instant.MIN;

    Throttle(int limit, Duration window, Clock clock) {
        this(limit, window, clock, MAX_KEYS);
    }

    /** With another cap: tests reach it without a hundred thousand keys. */
    Throttle(int limit, Duration window, Clock clock, int maxKeys) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
        this.maxKeys = maxKeys;
    }

    /**
     * The key for a client's address. IPv6 counts by its /64, the block a single
     * subscriber is given: per address, one client rotating through its own block
     * would start afresh with every request, and the per-client limit and captcha
     * would never be reached. IPv4, and anything that is not an address literal,
     * stays as it is.
     */
    static String clientKey(String remoteAddress) {
        if (remoteAddress == null) {
            return "";
        }
        try {
            InetAddress address = InetAddress.ofLiteral(remoteAddress);
            if (address instanceof Inet6Address) {
                byte[] prefix = Arrays.copyOf(address.getAddress(), 16);
                Arrays.fill(prefix, 8, 16, (byte) 0);
                return InetAddress.getByAddress(prefix).getHostAddress() + "/64";
            }
            return address.getHostAddress();
        } catch (IllegalArgumentException | java.net.UnknownHostException e) {
            return remoteAddress;
        }
    }

    /** Whether the key has used up its window. 0 means unlimited, as for the quotas. */
    boolean exhausted(String key) {
        if (limit == 0) {
            return false;
        }
        Window current = windows.get(key);
        return current != null && !current.over(clock.instant()) && current.count() >= limit;
    }

    /** Events for the key in its current window. */
    int count(String key) {
        Window current = windows.get(key);
        return current == null || current.over(clock.instant()) ? 0 : current.count();
    }

    /** Forgets the key, as after a successful sign-in. */
    void clear(String key) {
        windows.remove(key);
    }

    void record(String key) {
        Instant now = clock.instant();
        sweep(now);
        if (windows.size() >= maxKeys && !windows.containsKey(key)) {
            // Full of windows still running: this key goes uncounted, and is
            // answered as such, rather than evicting a key that is.
            return;
        }
        windows.compute(key, (k, w) -> w == null || w.over(now)
                ? new Window(now.plus(window), 1)
                : new Window(w.endsAt(), w.count() + 1));
    }

    /**
     * Whether this key would go uncounted: the map is full of running windows and
     * the key has none of them. Its count reads zero, which must not be taken to
     * mean "no failures" - it means "not known".
     */
    boolean uncounted(String key) {
        Instant now = clock.instant();
        sweep(now);
        if (windows.size() < maxKeys) {
            return false;
        }
        Window current = windows.get(key);
        return current == null || current.over(now);
    }

    /** Whether no new key can be counted now: the map is full of running windows. */
    boolean full() {
        sweep(clock.instant());
        return windows.size() >= maxKeys;
    }

    /** Drops the windows that ended, at most once per interval, whatever the request rate. */
    private void sweep(Instant now) {
        if (now.isBefore(nextSweep)) {
            return;
        }
        nextSweep = now.plus(SWEEP_EVERY);
        windows.values().removeIf(w -> w.over(now));
    }

    /** How many keys are held: for tests of the bound. */
    int size() {
        return windows.size();
    }

    private record Window(Instant endsAt, int count) {
        boolean over(Instant now) {
            return !now.isBefore(endsAt);
        }
    }
}
