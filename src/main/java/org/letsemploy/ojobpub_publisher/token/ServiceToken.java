package org.letsemploy.ojobpub_publisher.token;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.common.Base;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.security.UserEntity;

/** A credential letting another system act on one employer (spec 3.10). */
@Entity
@Table(name = "service_tokens")
@Getter
@Setter
@NoArgsConstructor
public class ServiceToken extends Base {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "employer_id", nullable = false, updatable = false)
    private Employer employer;

    @Column(nullable = false, length = 64)
    private String name;

    /** Identifies the token without revealing it; safe for logs and audit records. */
    @Column(nullable = false, length = 16, updatable = false)
    private String prefix;

    /** The secret is never stored (spec 2.8). */
    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_token_scopes",
            joinColumns = @JoinColumn(name = "service_token_id"))
    @Column(name = "scope", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<TokenScope> scopes = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private UserEntity createdBy;

    /** What makes a dormant integration visible; there is no expiry (spec 2.8). */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /**
     * When this token stops working, or null for never (spec 2.8). Nothing writes
     * it as time passes; a lapsed token is simply one whose date is behind us.
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Past its date. Recoverable, unlike {@link #isRevoked()}. */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean hasScope(TokenScope scope) {
        return scopes.stream().anyMatch(held -> held.implied().contains(scope));
    }

    /** How the token is named wherever it acted (spec 3.11). */
    public String getLabel() {
        return name + " (" + prefix + ")";
    }
}
