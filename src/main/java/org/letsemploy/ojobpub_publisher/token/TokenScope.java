package org.letsemploy.ojobpub_publisher.token;

import java.util.Set;

/**
 * What a service token may do (spec 2.8). Six, in three pairs.
 *
 * <p>The split exists because the blast radii differ: a feed-publishing
 * integration has no business editing postings, and almost nothing has business
 * removing colleagues. A scope is a ceiling, not a grant - an action needs the
 * membership role as well.
 */
public enum TokenScope {
    JOBS_READ, JOBS_WRITE,
    FEEDS_READ, FEEDS_WRITE,
    PEOPLE_READ, PEOPLE_WRITE;

    /** Writing implies reading; nothing else is implied. */
    public Set<TokenScope> implied() {
        return switch (this) {
            case JOBS_WRITE -> Set.of(JOBS_WRITE, JOBS_READ);
            case FEEDS_WRITE -> Set.of(FEEDS_WRITE, FEEDS_READ);
            case PEOPLE_WRITE -> Set.of(PEOPLE_WRITE, PEOPLE_READ);
            default -> Set.of(this);
        };
    }
}
