package org.letsemploy.ojobpub_publisher.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * The in-memory throttle behind sign-in and the mail-sending forms (spec 2.12):
 * it counts, it forgets ended windows, it cannot be grown without bound, and a
 * client is one key however many addresses of its own block it uses.
 * Plain unit test: no Spring, no database.
 */
class ThrottleTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    /** A clock the test moves. */
    private static final class Moving extends Clock {
        private Instant now = Instant.parse("2026-10-29T08:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Test
    void itCountsToTheLimitAndForgetsWhenTheWindowEnds() {
        Moving clock = new Moving();
        Throttle throttle = new Throttle(3, WINDOW, clock);
        for (int i = 0; i < 3; i++) {
            assertThat(throttle.exhausted("k")).isFalse();
            throttle.record("k");
        }
        assertThat(throttle.exhausted("k")).isTrue();

        clock.advance(WINDOW);
        assertThat(throttle.exhausted("k")).isFalse();
        assertThat(throttle.count("k")).isZero();
    }

    /** A flood of distinct keys cannot grow the map past its cap. */
    @Test
    void itHoldsNoMoreThanItsCap() {
        Throttle throttle = new Throttle(3, WINDOW, new Moving(), 100);
        for (int i = 0; i < 1_000; i++) {
            throttle.record("key-" + i);
        }
        assertThat(throttle.size()).isLessThanOrEqualTo(100);
        assertThat(throttle.count("key-999")).as("the newest key is counted").isEqualTo(1);
    }

    /** Ended windows go at the next sweep, not only once the map is large. */
    @Test
    void endedWindowsAreSweptAway() {
        Moving clock = new Moving();
        Throttle throttle = new Throttle(3, WINDOW, clock);
        for (int i = 0; i < 50; i++) {
            throttle.record("old-" + i);
        }
        clock.advance(WINDOW.plus(Throttle.SWEEP_EVERY));
        throttle.record("new");
        assertThat(throttle.size()).isEqualTo(1);
    }

    /** One subscriber's /64 is one client: rotating through it starts nothing afresh. */
    @Test
    void anIpv6ClientIsItsSlash64() {
        assertThat(Throttle.clientKey("2001:db8:1:2::1"))
                .isEqualTo(Throttle.clientKey("2001:db8:1:2:ffff:abcd:1234:5678"))
                .isEqualTo("2001:db8:1:2:0:0:0:0/64");
        assertThat(Throttle.clientKey("2001:db8:1:3::1")).isNotEqualTo(Throttle.clientKey("2001:db8:1:2::1"));
    }

    @Test
    void ipv4AndAnythingElseStayAsTheyAre() {
        assertThat(Throttle.clientKey("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(Throttle.clientKey("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(Throttle.clientKey("unknown")).isEqualTo("unknown");
        assertThat(Throttle.clientKey(null)).isEmpty();
    }

    /** Through LoginAttempts: failures from one /64 add up to the per-client limit. */
    @Test
    void failuresFromOneBlockAddUpPerClient() {
        Throttle perClient = new Throttle(5, WINDOW, new Moving());
        for (int i = 1; i <= 5; i++) {
            perClient.record(Throttle.clientKey("2001:db8:1:2::" + Integer.toHexString(i)));
        }
        assertThat(perClient.exhausted(Throttle.clientKey("2001:db8:1:2::99"))).isTrue();
    }
}
