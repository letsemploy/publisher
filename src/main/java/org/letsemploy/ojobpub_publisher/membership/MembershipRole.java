package org.letsemploy.ojobpub_publisher.membership;

/**
 * What a person is to one employer (spec 2.1).
 *
 * <p>Independent of the platform role: a plain user may own their own employer
 * while having no standing anywhere else.
 */
public enum MembershipRole {
    /** May edit the employer record, invite people, change roles and remove members. */
    OWNER,
    /** May work on the employer's jobs, feeds and locations. */
    EDITOR;

    public boolean isOwner() {
        return this == OWNER;
    }
}
