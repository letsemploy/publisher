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

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "invited_by_id", nullable = false, updatable = false)
    private UserEntity invitedBy;

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
        this.employer = employer;
        this.invitee = invitee;
        this.invitedBy = invitedBy;
        this.role = role;
        this.status = InvitationStatus.PENDING;
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
