package org.letsemploy.ojobpub_publisher.security;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
    private Role role = Role.EDITOR;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_employers", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "employer_id")
    private Set<UUID> employerIds = new HashSet<>();

    public enum Role {
        EDITOR, ADMIN
    }
}
