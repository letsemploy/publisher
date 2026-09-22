package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;
import lombok.Value;

/** The employer People screen: members, pending invitations, and the invite form. */
@Value
public class PeopleView {
    String employerId;
    String employerName;
    List<MemberRow> members;
    List<PendingInvitationRow> pending;
}
