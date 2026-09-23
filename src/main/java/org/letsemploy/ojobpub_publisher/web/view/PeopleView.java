package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;
import lombok.Value;

/**
 * The People screen (spec 7.18): who belongs to an employer.
 *
 * <p>Every member sees {@link #members}. Only an owner or an admin gets
 * {@link #pending} and the invite form, which is why {@code canAdminister} is
 * carried here rather than inferred in the template - and why {@code pending} is
 * empty rather than hidden for everyone else.
 */
@Value
public class PeopleView {
    String employerId;
    String employerName;
    List<MemberRow> members;
    List<PendingInvitationRow> pending;
    boolean canAdminister;
}
