package org.letsemploy.ojobpub_publisher.membership;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.common.Base;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.security.UserEntity;

/** One person's standing in one employer (spec 3.9). */
@Entity
@Table(name = "memberships")
@Getter
@Setter
@NoArgsConstructor
public class Membership extends Base {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "employer_id", nullable = false, updatable = false)
    private Employer employer;

    /** The only thing about a membership that may change (spec 2.7). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipRole role = MembershipRole.EDITOR;

    public Membership(UserEntity user, Employer employer, MembershipRole role) {
        this.user = user;
        this.employer = employer;
        this.role = role;
    }
}
