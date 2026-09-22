package org.letsemploy.ojobpub_publisher.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Value;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.token.TokenScope;

/**
 * Whoever is acting: a signed-in user, or a service token (spec 3.11).
 *
 * <p>One type rather than two, because every rule downstream asks the same
 * questions of both - may you see this employer, are you an owner of it - and a
 * second principal type would mean every service answering them twice.
 */
@Value
public class Actor {

    /** The user's id, or the token's. Null only for the anonymous fallback. */
    UUID id;
    String displayName;
    String email;
    /** The platform role (spec 2.1). A token is never an admin. */
    boolean admin;
    /** Per-employer standing (spec 2.1). A token has exactly one entry. */
    Map<UUID, MembershipRole> memberships;
    /** Empty for a person: scopes narrow a token only (spec 2.8). */
    Set<TokenScope> scopes;
    /** True when this is a service token rather than a person. */
    boolean token;

    public static Actor user(UUID id, String displayName, String email, boolean admin,
                             Map<UUID, MembershipRole> memberships) {
        return new Actor(id, displayName, email, admin, memberships, Set.of(), false);
    }

    public static Actor serviceToken(UUID id, String label, UUID employerId,
                                     MembershipRole role, Set<TokenScope> scopes) {
        return new Actor(id, label, null, false, Map.of(employerId, role), scopes, true);
    }

    public static Actor anonymous() {
        return new Actor(null, "anonymous", null, false, Map.of(), Set.of(), false);
    }

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

    /** A person may do anything their role allows; a token needs the scope too (spec 2.8). */
    public boolean hasScope(TokenScope scope) {
        return !token || scopes.stream().anyMatch(held -> held.implied().contains(scope));
    }

    public boolean isAnonymous() {
        return id == null;
    }
}
