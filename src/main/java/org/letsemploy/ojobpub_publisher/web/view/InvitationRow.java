package org.letsemploy.ojobpub_publisher.web.view;

/** One pending invitation on the invitee's own list (spec 7.16). */
public record InvitationRow(String id, String employerName, String role, String invitedBy, String invitedAt) {
}
