package org.letsemploy.ojobpub_publisher.web.view;

/** Someone with access to an employer, on the People screen (spec 7.13). */
public record MemberRow(
        String id,
        String displayName,
        Avatar avatar,
        String email,
        String role,
        /** The last active owner cannot be demoted, removed or suspended (spec 2.7). */
        boolean lastOwner,
        /** When the membership was suspended, or null while it is active (spec 2.7). */
        String suspendedAt,
        /** The viewer's own row: nobody may suspend themselves. */
        boolean self) {

    public boolean isSuspended() {
        return suspendedAt != null;
    }
}
