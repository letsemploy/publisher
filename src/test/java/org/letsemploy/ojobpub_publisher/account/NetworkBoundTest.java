package org.letsemploy.ojobpub_publisher.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * One rented IPv6 /48 cannot fill the failure throttles (spec 2.12). Rotating
 * through its /64s starts a new client each time, but every failure also counts
 * against the /48, so it is refused after a few clients' worth - long before the
 * maps are full and everyone else's sign-ins go uncounted.
 *
 * <p>Plain unit test: no Spring, no database.
 */
class NetworkBoundTest {

    private static final int CAP = 1_000;
    /** max-attempts 10: 50 per client, 200 per network. */
    private static final int PER_NETWORK = 10 * 5 * Throttle.CLIENTS_PER_NETWORK;

    private final LoginAttempts attempts = new LoginAttempts(new LocalAccountProperties(true, 10, 3, 10,
            Duration.ofMinutes(15), new LocalAccountProperties.Password(12, 128, 0, 0)), CAP);

    /** A fresh /64 of 2001:db8:1::/48 for every attempt, and a fresh address too. */
    private static String client(int i) {
        return "2001:db8:1:" + Integer.toHexString(i) + "::1";
    }

    @Test
    void aNetworkIsRefusedAfterItsBudgetWhateverClientItUses() {
        int tried = 0;
        while (!attempts.refused("victim-" + tried + "@example.com", client(tried))) {
            attempts.failed("victim-" + tried + "@example.com", client(tried));
            tried++;
            assertThat(tried).as("refused before the maps fill").isLessThan(CAP);
        }
        assertThat(tried).isEqualTo(PER_NETWORK);
        assertThat(attempts.refused("anyone@example.com", client(60_000))).isTrue();
    }

    @Test
    void anotherNetworkIsCountedAsUsual() {
        for (int i = 0; i < PER_NETWORK; i++) {
            attempts.failed("victim-" + i + "@example.com", client(i));
        }
        String elsewhere = "2001:db8:2::1";
        assertThat(attempts.refused("owner@example.com", elsewhere)).isFalse();
        assertThat(attempts.uncounted("owner@example.com", elsewhere)).isFalse();
        assertThat(attempts.captchaRequired("owner@example.com", elsewhere)).isFalse();
    }
}
