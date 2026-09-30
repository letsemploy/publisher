package org.letsemploy.ojobpub_publisher.web.view;

/** A pending invitation as the inviting admin sees it (spec 7.13). */
public record PendingInvitationRow(
        String id,
        String inviteeName,
        String inviteeEmail,
        String role,
        String invitedBy,
        String invitedAt) {
}
