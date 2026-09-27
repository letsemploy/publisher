package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

/** One person on the Users screen (spec 7.20). */
@Value
public class UserRow {
    String id;
    String displayName;
    String email;
    /** The identity provider's host, or "development" for the seeded users. */
    String provider;
    boolean admin;
    long employers;
    /** Why this row offers no "View as": "admin", "self", or null when it does (spec 2.9). */
    String notViewableBecause;

    public boolean isViewable() {
        return notViewableBecause == null;
    }
}
