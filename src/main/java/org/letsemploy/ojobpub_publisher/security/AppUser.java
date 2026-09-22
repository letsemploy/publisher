package org.letsemploy.ojobpub_publisher.security;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Value;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;

/**
 * The authenticated user as the application sees them (spec 2.1, 2.2).
 *
 * <p>Carries a stable {@link #id} and {@link #email} because an invitation is
 * addressed to a person: a principal that cannot be identified as a specific user
 * has nothing to attach one to.
 */
@Value
public class AppUser {
    UUID id;
    String displayName;
    String email;
    /** The platform role (spec 2.1): staff who may act on any employer. */
    boolean admin;
    /** Per-employer standing (spec 2.1). Ignored when {@link #admin} is true. */
    Map<UUID, MembershipRole> memberships;

    /** Employers this user belongs to. */
    public List<UUID> getEmployerIds() {
        return List.copyOf(memberships.keySet());
    }

    /**
     * Owner of this employer? False for an admin who is not a member - an admin
     * may administer any employer, but through the platform role, not this one.
     */
    public boolean isOwnerOf(UUID employerId) {
        MembershipRole role = memberships.get(employerId);
        return role != null && role.isOwner();
    }

    /** Nobody is signed in; used only by the anonymous fallback. */
    public boolean isAnonymous() {
        return id == null;
    }
}
