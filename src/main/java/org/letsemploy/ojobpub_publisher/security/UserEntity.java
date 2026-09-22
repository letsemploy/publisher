package org.letsemploy.ojobpub_publisher.security;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
@NoArgsConstructor
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
     * The platform role (spec 2.1). Per-employer standing lives on the membership,
     * not here: {@code EDITOR} named a global role that no longer exists.
     */
    public enum Role {
        USER, ADMIN
    }
}
