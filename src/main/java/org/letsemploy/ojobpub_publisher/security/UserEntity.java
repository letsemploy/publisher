package org.letsemploy.ojobpub_publisher.security;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.common.Base;

/**
 * A back-office user, identified by the stable (issuer, subject) pair from the ID
 * token - never by email, which is mutable and may be reassigned (spec 2.2).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class UserEntity extends Base {

    @Column(nullable = false)
    private String issuer;

    @Column(nullable = false)
    private String subject;

    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    /**
     * Set while a platform admin has suspended the account (spec 2.11): it cannot
     * sign in, and an open session ends. Null means active.
     */
    private Instant suspendedAt;

    public boolean isSuspended() {
        return suspendedAt != null;
    }

    /**
     * The platform role (spec 2.1). Per-employer standing lives on the membership,
     * not here: {@code EDITOR} named a global role that no longer exists.
     */
    public enum Role {
        USER, ADMIN
    }

    /** What to call them: the name, else the email, else the provider's subject. */
    public String getLabel() {
        return displayName != null && !displayName.isBlank() ? displayName
                : email != null ? email : subject;
    }
}
