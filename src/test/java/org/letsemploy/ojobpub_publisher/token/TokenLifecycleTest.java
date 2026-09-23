package org.letsemploy.ojobpub_publisher.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * The expiry rule (spec 2.8), with no Spring and no database.
 *
 * <p>The boundaries are the point. "Expires on the 23rd" has to mean something
 * exact, and a token must not be refused a moment early or honoured a moment
 * late; nor may the warning window swallow the difference between a token that
 * still works and one that does not.
 */
class TokenLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final int WARN = 30;

    private static ServiceToken token(Instant expiresAt, Instant revokedAt) {
        ServiceToken token = new ServiceToken();
        token.setExpiresAt(expiresAt);
        token.setRevokedAt(revokedAt);
        return token;
    }

    @Test
    void aTokenWellInsideItsDateIsActive() {
        assertThat(TokenLifecycle.state(token(NOW.plus(Duration.ofDays(90)), null), NOW, WARN))
                .isEqualTo(TokenLifecycle.State.ACTIVE);
    }

    @Test
    void aTokenWithNoDateNeverExpires() {
        assertThat(TokenLifecycle.state(token(null, null), NOW, WARN))
                .isEqualTo(TokenLifecycle.State.ACTIVE);
    }

    /** Exactly at the threshold counts as expiring: the warning is inclusive. */
    @Test
    void theWarningWindowIsInclusiveAtItsEdge() {
        assertThat(TokenLifecycle.state(token(NOW.plus(Duration.ofDays(30)), null), NOW, WARN))
                .isEqualTo(TokenLifecycle.State.EXPIRING);
        assertThat(TokenLifecycle.state(
                token(NOW.plus(Duration.ofDays(30)).plusSeconds(1), null), NOW, WARN))
                .isEqualTo(TokenLifecycle.State.ACTIVE);
    }

    /** Exactly at the instant it expires, it is expired - not one second later. */
    @Test
    void expiryIsInclusiveAtTheInstant() {
        assertThat(TokenLifecycle.state(token(NOW, null), NOW, WARN))
                .isEqualTo(TokenLifecycle.State.EXPIRED);
        assertThat(TokenLifecycle.state(token(NOW.plusSeconds(1), null), NOW, WARN))
                .isEqualTo(TokenLifecycle.State.EXPIRING);
    }

    /**
     * Revoked outranks expired. One is a decision and final; the other is the
     * calendar and reversible, and the screen must not offer Reactivate on a
     * token that can never come back.
     */
    @Test
    void revokedOutranksExpired() {
        assertThat(TokenLifecycle.state(token(NOW.minusSeconds(1), NOW), NOW, WARN))
                .isEqualTo(TokenLifecycle.State.REVOKED);
    }

    @Test
    void aZeroWarningWindowNeverFlagsAnythingAsExpiring() {
        assertThat(TokenLifecycle.state(token(NOW.plusSeconds(60), null), NOW, 0))
                .isEqualTo(TokenLifecycle.State.ACTIVE);
    }

    // --- computing the next date -------------------------------------------

    /** Calendar months, not 30-day blocks: twelve months is a year, not 360 days. */
    @Test
    void twelveMonthsIsACalendarYear() {
        Instant expiry = TokenLifecycle.expiryFrom(NOW, 12);
        assertThat(expiry.atZone(ZoneId.systemDefault()).toLocalDate())
                .isEqualTo(NOW.atZone(ZoneId.systemDefault()).toLocalDate().plusYears(1));
    }

    @Test
    void aLifetimeOfZeroMeansNeverExpires() {
        assertThat(TokenLifecycle.expiryFrom(NOW, 0)).isNull();
        assertThat(TokenLifecycle.expiryFrom(NOW, -1)).isNull();
    }
}
