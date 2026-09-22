package org.letsemploy.ojobpub_publisher.invitation;

import java.util.List;

/** Lifecycle of an invitation (spec 3.8). A resolved invitation is history. */
public enum InvitationStatus {
    PENDING, ACCEPTED, DECLINED, REVOKED;

    public List<InvitationStatus> allowedTransitions() {
        return this == PENDING ? List.of(ACCEPTED, DECLINED, REVOKED) : List.of();
    }

    public boolean isPending() {
        return this == PENDING;
    }
}
