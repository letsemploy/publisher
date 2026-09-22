package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

/** Someone with access to an employer, on the People screen (spec 7.13). */
@Value
public class MemberRow {
    String id;
    String displayName;
    String email;
    String role;
    /** The last owner cannot be demoted or removed (spec 2.7). */
    boolean lastOwner;
}
