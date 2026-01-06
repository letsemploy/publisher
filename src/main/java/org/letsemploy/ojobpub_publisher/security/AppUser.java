package org.letsemploy.ojobpub_publisher.security;

import java.util.List;
import java.util.UUID;
import lombok.Value;

/** The authenticated user as the application sees them (spec 2.1). */
@Value
public class AppUser {
    String displayName;
    boolean admin;
    /** Employers this user may see. Ignored when {@link #admin} is true. */
    List<UUID> employerIds;
}
