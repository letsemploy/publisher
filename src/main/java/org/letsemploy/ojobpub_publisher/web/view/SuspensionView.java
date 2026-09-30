package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;

/**
 * The confirmation before suspending an account (spec 2.11): whose, and what is
 * left without an active owner by it.
 */
public record SuspensionView(
        String id,
        String displayName,
        String email,
        /** Employers this person is the last active owner of, by name. */
        List<String> soleOwnerships) {
}
