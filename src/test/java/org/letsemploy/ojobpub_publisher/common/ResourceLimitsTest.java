package org.letsemploy.ojobpub_publisher.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;

/**
 * The quota convention itself (spec 8.4), with no Spring and no database.
 *
 * <p>Worth its own test because the convention is the part that would rot: the
 * six numbers are configuration, but "0 means unlimited" and "refuse at the cap,
 * not past it" are rules, and they live in exactly one place so that they cannot
 * be re-implemented differently at the sixth call site.
 */
class ResourceLimitsTest {

    private static ResourceLimits limits(int all) {
        return new ResourceLimits(all, all, all, all, all, all);
    }

    @Test
    void zeroMeansUnlimited() {
        ResourceLimits limits = limits(0);
        assertThatCode(() -> limits.requireRoomForJobs(() -> Long.MAX_VALUE))
                .doesNotThrowAnyException();
        assertThatCode(() -> limits.requireRoomForMemberships(() -> Long.MAX_VALUE))
                .doesNotThrowAnyException();
    }

    /**
     * An unlimited quota must not even ask. Creating a job is on the API's write
     * path, and a disabled limit should cost nothing at all - which is the whole
     * reason the count arrives as a supplier rather than a number.
     */
    @Test
    void anUnlimitedQuotaIssuesNoCount() {
        AtomicBoolean counted = new AtomicBoolean(false);
        limits(0).requireRoomForJobs(() -> {
            counted.set(true);
            return 0L;
        });
        assertThat(counted).isFalse();
    }

    /** At the cap is already too many; the row being added would be the fourth. */
    @Test
    void theRefusalIsAtTheCapNotPastIt() {
        ResourceLimits limits = limits(3);
        assertThatCode(() -> limits.requireRoomForFeeds(() -> 2L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limits.requireRoomForFeeds(() -> 3L))
                .isInstanceOf(ValidationFailure.class);
        assertThatThrownBy(() -> limits.requireRoomForFeeds(() -> 4L))
                .isInstanceOf(ValidationFailure.class);
    }

    /**
     * The key is what carries the refusal onwards: ApiErrors derives the stable
     * API code from it, and the message bundles are keyed on it.
     */
    @Test
    void eachQuotaCarriesItsOwnKeyAndNamesTheNumber() {
        assertThatThrownBy(() -> limits(2).requireRoomForTokens(() -> 2L))
                .isInstanceOf(ValidationFailure.class)
                .satisfies(e -> {
                    var errors = ((ValidationFailure) e).getFieldErrors();
                    assertThat(errors).containsOnlyKeys("limit.tokens");
                    assertThat(errors.get("limit.tokens")).contains("2").contains("Revoke");
                });
    }

    /** Six quotas, six distinct keys - no two refusals can be confused. */
    @Test
    void theSixQuotasHaveDistinctKeys() {
        ResourceLimits limits = limits(1);
        assertThat(java.util.stream.Stream.<Runnable>of(
                        () -> limits.requireRoomForMemberships(() -> 1L),
                        () -> limits.requireRoomForJobs(() -> 1L),
                        () -> limits.requireRoomForFeeds(() -> 1L),
                        () -> limits.requireRoomForInvitations(() -> 1L),
                        () -> limits.requireRoomForTokens(() -> 1L),
                        () -> limits.requireRoomForMembers(() -> 1L))
                .map(r -> {
                    try {
                        r.run();
                        throw new AssertionError("expected a refusal");
                    } catch (ValidationFailure e) {
                        return e.getFieldErrors().keySet().iterator().next();
                    }
                })
                .distinct().count()).isEqualTo(6);
    }
}
