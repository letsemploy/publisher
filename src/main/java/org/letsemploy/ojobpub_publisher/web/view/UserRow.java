package org.letsemploy.ojobpub_publisher.web.view;

/** One person on the Users screen (spec 7.20). */
public record UserRow(
        String id,
        String displayName,
        Avatar avatar,
        String email,
        /** The identity provider's host, or "development" for the seeded users. */
        String provider,
        boolean admin,
        long employers,
        /**
        * Why this row can be neither viewed as nor suspended: "admin", "self", or
        * null when it can (spec 2.9, 2.11). The two share their exceptions.
        */
        String notViewableBecause,
        /** When the account was suspended, or null while it is active (spec 2.11). */
        String suspendedAt) {

    public boolean isViewable() {
        return notViewableBecause == null;
    }

    public boolean isSuspended() {
        return suspendedAt != null;
    }
}
