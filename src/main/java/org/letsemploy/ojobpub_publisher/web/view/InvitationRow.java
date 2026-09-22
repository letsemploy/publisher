package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

/** One pending invitation on the invitee's own list (spec 7.16). */
@Value
public class InvitationRow {
    String id;
    String employerName;
    String role;
    String invitedBy;
    String invitedAt;
}
