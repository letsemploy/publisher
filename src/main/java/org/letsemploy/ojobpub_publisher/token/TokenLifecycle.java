package org.letsemploy.ojobpub_publisher.token;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * What a service token presents as, and when it next lapses (spec 2.8).
 *
 * <p>Modelled on {@code job/Publication}: a static rule that derives a presented
 * value from stored columns plus the clock, and never writes. Expiry therefore
 * changes nothing in the database as it happens - a token that lapses on Sunday
 * is not touched on Sunday - which is what makes reactivating one a single date
 * change rather than an undo, and what keeps this project free of a scheduler.
 */
public final class TokenLifecycle {

    private TokenLifecycle() {
    }

    /**
     * Four states, of which only {@link #REVOKED} is permanent.
     *
     * <p>Named {@code State} rather than reusing the word "presentation" so that
     * it cannot be mistaken for {@code Publication.Presentation}, which answers a
     * different question about a different thing.
     */
    public enum State {
        /** Live, and not near its date. */
        ACTIVE,
        /** Live, but inside the warning window: renew it before it lapses. */
        EXPIRING,
        /** Past its date and refusing requests, but reactivatable. */
        EXPIRED,
        /** Deliberately killed. Never comes back (spec 2.8). */
        REVOKED
    }

    public static State state(ServiceToken token, Instant now, int warningDays) {
        // Revoked outranks expired: one is a decision, the other is the calendar,
        // and only one of them is final.
        if (token.isRevoked()) {
            return State.REVOKED;
        }
        if (token.isExpired(now)) {
            return State.EXPIRED;
        }
        if (token.getExpiresAt() != null && warningDays > 0
                && !token.getExpiresAt().isAfter(now.plus(Duration.ofDays(warningDays)))) {
            return State.EXPIRING;
        }
        return State.ACTIVE;
    }

    /**
     * When a token created or renewed now should lapse.
     *
     * <p>Null when the lifetime is 0, which is how an installation switches expiry
     * off - the same convention {@code ResourceLimits} uses for its quotas.
     */
    public static Instant expiryFrom(Instant now, int lifetimeMonths) {
        if (lifetimeMonths <= 0) {
            return null;
        }
        // Calendar months, not 30-day blocks: Instant cannot add months at all,
        // and approximating would make a promised twelve months 360 days.
        return now.atZone(ZoneId.systemDefault()).plusMonths(lifetimeMonths).toInstant();
    }
}
