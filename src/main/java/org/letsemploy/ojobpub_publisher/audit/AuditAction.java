package org.letsemploy.ojobpub_publisher.audit;

import java.util.Arrays;
import java.util.List;

/**
 * Every kind of event the audit log records, and who may read it (spec 3.12).
 *
 * <p>The audience lives here, once, rather than in each query or screen: an
 * employer's log shows {@link Audience#MEMBERS} events to every member and
 * {@link Audience#OWNERS} events only to those who administer it, because the
 * log must not show an editor what the People and API tokens screens withhold.
 * {@link Audience#PERSONAL} events have no employer and appear only in the log of
 * the person they are about - and to admins.
 *
 * <p>Stored by name. Renaming a constant orphans the rows already written.
 */
public enum AuditAction {

    EMPLOYER_CREATED(Audience.MEMBERS),
    EMPLOYER_UPDATED(Audience.MEMBERS),
    EMPLOYER_DELETED(Audience.MEMBERS),

    JOB_CREATED(Audience.MEMBERS),
    JOB_UPDATED(Audience.MEMBERS),
    JOB_STATUS_CHANGED(Audience.MEMBERS),
    JOB_DELETED(Audience.MEMBERS),

    FEED_CREATED(Audience.MEMBERS),
    FEED_UPDATED(Audience.MEMBERS),
    FEED_DELETED(Audience.MEMBERS),
    FEED_JOB_ADDED(Audience.MEMBERS),
    FEED_JOB_REMOVED(Audience.MEMBERS),

    LOCATION_CREATED(Audience.MEMBERS),
    LOCATION_UPDATED(Audience.MEMBERS),
    LOCATION_DELETED(Audience.MEMBERS),

    TAG_CREATED(Audience.MEMBERS),
    TAG_UPDATED(Audience.MEMBERS),
    TAG_DELETED(Audience.MEMBERS),

    // Who belongs is on the People screen, which every member sees (spec 7.18).
    MEMBER_JOINED(Audience.MEMBERS),
    MEMBER_ROLE_CHANGED(Audience.MEMBERS),
    MEMBER_SUSPENDED(Audience.MEMBERS),
    MEMBER_REINSTATED(Audience.MEMBERS),
    MEMBER_REMOVED(Audience.MEMBERS),

    // Pending invitations and tokens are shown to owners only (spec 7.17, 7.18).
    INVITATION_SENT(Audience.OWNERS),
    INVITATION_REVOKED(Audience.OWNERS),
    INVITATION_DECLINED(Audience.OWNERS),

    TOKEN_CREATED(Audience.OWNERS),
    TOKEN_RENEWED(Audience.OWNERS),
    TOKEN_REVOKED(Audience.OWNERS),

    ACCOUNT_CREATED(Audience.PERSONAL),
    ADMIN_GRANTED(Audience.PERSONAL),
    ADMIN_REVOKED(Audience.PERSONAL),
    ACCOUNT_SUSPENDED(Audience.PERSONAL),
    ACCOUNT_REINSTATED(Audience.PERSONAL),
    ADMIN_MODE_ENTERED(Audience.PERSONAL),
    ADMIN_MODE_LEFT(Audience.PERSONAL),
    VIEW_AS_STARTED(Audience.PERSONAL),
    VIEW_AS_STOPPED(Audience.PERSONAL);

    public enum Audience {
        MEMBERS, OWNERS, PERSONAL
    }

    private final Audience audience;

    AuditAction(Audience audience) {
        this.audience = audience;
    }

    public Audience audience() {
        return audience;
    }

    /** What an editor's view of an employer's log leaves out. */
    public static List<AuditAction> ownersOnly() {
        return Arrays.stream(values()).filter(a -> a.audience == Audience.OWNERS).toList();
    }

    /** Whether the detail holds membership roles, which the screen translates. */
    public boolean hasRoleDetail() {
        return this == MEMBER_ROLE_CHANGED || this == MEMBER_JOINED || this == INVITATION_SENT
                || this == TOKEN_CREATED;
    }
}
