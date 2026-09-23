package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;
import lombok.Value;

/** A service token on the API tokens screen (spec 7.17). */
@Value
public class TokenRow {
    String id;
    String name;
    String prefix;
    List<String> scopes;
    String createdBy;
    String createdAt;
    /** Null when never used - what makes a forgotten integration visible. */
    String lastUsedAt;
    /** Null when it never expires, which a lifetime of 0 configures (spec 2.8). */
    String expiresAt;
    /** Lower-cased {@code TokenLifecycle.State}: active, expiring, expired, revoked. */
    String state;
    boolean revoked;

    /** Renewing a revoked token is refused, so the control is not offered. */
    public boolean isRenewable() {
        return !revoked;
    }

    public boolean isExpired() {
        return "expired".equals(state);
    }
}
