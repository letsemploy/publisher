package org.letsemploy.ojobpub_publisher.api;

import lombok.Value;

/**
 * A rejected input or a broken rule, returned as data beside the payload
 * (spec 11.4). Built by {@link ApiErrors}.
 */
@Value
public class UserError {
    /** The input field that fixes it, or null when no single field does. */
    String field;
    String message;
    /** Stable, so a client branches on this and not on the message text. */
    String code;
}
