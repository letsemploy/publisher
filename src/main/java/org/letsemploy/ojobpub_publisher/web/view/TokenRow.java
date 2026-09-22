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
    boolean revoked;
}
