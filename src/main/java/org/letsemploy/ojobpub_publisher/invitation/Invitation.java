package org.letsemploy.ojobpub_publisher.invitation;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.common.Base;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.token.ServiceToken;

/** An offer of access to one employer, made to one registered user (spec 3.8). */
@Entity
@Table(name = "invitations")
@Getter
@Setter
@NoArgsConstructor
public class Invitation extends Base {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "employer_id", nullable = false, updatable = false)
    private Employer employer;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "invitee_id", nullable = false, updatable = false)
    private UserEntity invitee;

    /**
     * Who sent it - exactly one of these is set (spec 3.11). An audit record names
     * the token, not the person who created it: the token is what acted.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "invited_by_user_id", updatable = false)
    private UserEntity invitedByUser;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "invited_by_token_id", updatable = false)
    private ServiceToken invitedByToken;

    /** The membership role acceptance will grant (spec 2.6, 3.8). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipRole role = MembershipRole.EDITOR;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "responded_at")
    private Instant respondedAt;

    public Invitation(Employer employer, UserEntity invitee, UserEntity invitedBy,
                      MembershipRole role) {
        this(employer, invitee, role);
        this.invitedByUser = invitedBy;
    }

    public Invitation(Employer employer, UserEntity invitee, ServiceToken invitedBy,
                      MembershipRole role) {
        this(employer, invitee, role);
        this.invitedByToken = invitedBy;
    }

    private Invitation(Employer employer, UserEntity invitee, MembershipRole role) {
        this.employer = employer;
        this.invitee = invitee;
        this.role = role;
        this.status = InvitationStatus.PENDING;
    }

    /** How the inviter is named on screen and in audit records (spec 3.11). */
    public String getInvitedByLabel() {
        if (invitedByUser != null) {
            return invitedByUser.getDisplayName() != null
                    ? invitedByUser.getDisplayName() : invitedByUser.getEmail();
        }
        return invitedByToken.getLabel();
    }

    /**
     * Resolves the invitation. Only a pending one may be resolved, and the stamp is
     * set once - a resolved invitation is never reopened (spec 3.8).
     */
    public void resolve(InvitationStatus target) {
        if (!status.allowedTransitions().contains(target)) {
            throw new IllegalStateException("Cannot move invitation from " + status + " to " + target);
        }
        this.status = target;
        this.respondedAt = Instant.now();
    }
}
