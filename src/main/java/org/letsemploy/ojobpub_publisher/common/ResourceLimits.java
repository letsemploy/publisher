package org.letsemploy.ojobpub_publisher.common;

import java.util.function.LongSupplier;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The resource quotas of spec 8.4, in one place.
 *
 * <p>Named for what it bounds, to keep it apart from {@code api/ApiLimitsConfig}
 * and {@code api/ApiRateLimiter}, which bound the shape and rate of a request
 * rather than how much may exist.
 *
 * <p>The comparison lives here and not at the six call sites because of one
 * convention: <strong>0 means unlimited</strong>. Spread across six services,
 * that rule gets written six times and the sixth one forgets the zero, turning
 * "unlimited" into "none allowed".
 *
 * <p>Counts arrive as a {@link LongSupplier} rather than a number so that an
 * unlimited quota issues no {@code COUNT} at all - creating a job is on the
 * API's write path, and a disabled limit should cost nothing. It also lets the
 * test assert that nothing was counted.
 *
 * <p>This class deliberately injects no repositories. It lives in {@code common},
 * which depends on nothing; each service already owns the repository that can
 * answer its own count.
 */
@Component
public class ResourceLimits {

    private final int membershipsPerUser;
    private final int jobsPerEmployer;
    private final int feedsPerEmployer;
    private final int pendingInvitationsPerEmployer;
    private final int tokensPerEmployer;
    private final int membersPerEmployer;

    public ResourceLimits(
            @Value("${app.limits.memberships-per-user:3}") int membershipsPerUser,
            @Value("${app.limits.jobs-per-employer:100}") int jobsPerEmployer,
            @Value("${app.limits.feeds-per-employer:10}") int feedsPerEmployer,
            @Value("${app.limits.pending-invitations-per-employer:20}") int pendingInvitations,
            @Value("${app.limits.tokens-per-employer:10}") int tokensPerEmployer,
            @Value("${app.limits.members-per-employer:25}") int membersPerEmployer) {
        this.membershipsPerUser = membershipsPerUser;
        this.jobsPerEmployer = jobsPerEmployer;
        this.feedsPerEmployer = feedsPerEmployer;
        this.pendingInvitationsPerEmployer = pendingInvitations;
        this.tokensPerEmployer = tokensPerEmployer;
        this.membersPerEmployer = membersPerEmployer;
    }

    /** Counts a person's own memberships; a token's membership is not a person's. */
    public void requireRoomForMemberships(LongSupplier current) {
        check(membershipsPerUser, current, "limit.memberships",
                "You already belong to the maximum of %d employers. Leave one to join another.");
    }

    public void requireRoomForJobs(LongSupplier current) {
        check(jobsPerEmployer, current, "limit.jobs",
                "This employer has reached its limit of %d job postings. Delete one to add another.");
    }

    public void requireRoomForFeeds(LongSupplier current) {
        check(feedsPerEmployer, current, "limit.feeds",
                "This employer has reached its limit of %d feeds. Delete one to add another.");
    }

    public void requireRoomForInvitations(LongSupplier current) {
        check(pendingInvitationsPerEmployer, current, "limit.invitations",
                "This employer has reached its limit of %d pending invitations. "
                        + "Revoke one, or wait for an answer.");
    }

    public void requireRoomForTokens(LongSupplier current) {
        check(tokensPerEmployer, current, "limit.tokens",
                "This employer has reached its limit of %d API tokens. Revoke one to create another.");
    }

    public void requireRoomForMembers(LongSupplier current) {
        check(membersPerEmployer, current, "limit.members",
                "This employer has reached its limit of %d members. Remove one to add another.");
    }

    private void check(int limit, LongSupplier current, String key, String template) {
        if (limit <= 0) {
            // 0 disables the quota, and costs not even the count.
            return;
        }
        if (current.getAsLong() >= limit) {
            throw new ValidationFailure(key, template.formatted(limit));
        }
    }
}
