package org.letsemploy.ojobpub_publisher.membership;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.common.Base;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.token.ServiceToken;

/** One person's standing in one employer (spec 3.9). */
@Entity
@Table(name = "memberships")
@Getter
@Setter
@NoArgsConstructor
public class Membership extends Base {

    /** Exactly one of {@link #user} and {@link #serviceToken} is set (spec 3.11). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", updatable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_token_id", updatable = false)
    private ServiceToken serviceToken;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "employer_id", nullable = false, updatable = false)
    private Employer employer;

    /** What may change about a membership, with {@link #suspendedAt} (spec 2.7). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipRole role = MembershipRole.EDITOR;

    /**
     * Set while an owner has suspended this membership; null means active
     * (spec 2.7). A suspended membership is still listed and still holds its
     * role, but grants nothing.
     */
    private Instant suspendedAt;

    public Membership(UserEntity user, Employer employer, MembershipRole role) {
        this.user = user;
        this.employer = employer;
        this.role = role;
    }

    public Membership(ServiceToken serviceToken, Employer employer, MembershipRole role) {
        this.serviceToken = serviceToken;
        this.employer = employer;
        this.role = role;
    }

    /** True when this membership belongs to a person rather than a credential. */
    public boolean isHeldByUser() {
        return user != null;
    }

    public boolean isSuspended() {
        return suspendedAt != null;
    }

    /** How the member is named on screen and in audit records. */
    public String getMemberLabel() {
        if (user != null) {
            return user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
        }
        return serviceToken.getLabel();
    }
}
