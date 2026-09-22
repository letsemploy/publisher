package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

/** A pending invitation as the inviting admin sees it (spec 7.13). */
@Value
public class PendingInvitationRow {
    String id;
    String inviteeName;
    String inviteeEmail;
    String role;
    String invitedBy;
    String invitedAt;
}
