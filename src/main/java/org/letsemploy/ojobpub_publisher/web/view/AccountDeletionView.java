package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;

/**
 * The confirmation before deleting one's own account (spec 2.13): what to type,
 * what goes with it and what stays - or why it may not be deleted at all.
 */
public record AccountDeletionView(
        /** Typed back to confirm: the account's email, or its name when it has none. */
        String confirmWith,
        /** Employers this person is the last active owner of: deleted with everything, by name. */
        List<String> deletedEmployers,
        /** Employers they only leave: someone else still owns them. */
        List<String> leftEmployers,
        /** The only admin, so refused: the platform would have nobody to run it. */
        boolean onlyAdmin) {
}
