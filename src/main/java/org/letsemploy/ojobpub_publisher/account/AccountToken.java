package org.letsemploy.ojobpub_publisher.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.common.Base;

/**
 * A link mailed to a local account (spec 2.12): single-use, expiring, and stored
 * only as a SHA-256 of its secret.
 */
@Entity
@Table(name = "account_tokens")
@Getter
@Setter
public class AccountToken extends Base {

    public enum Purpose {
        /** Completes a sign-up: confirms the address and sets the first password. */
        VERIFY_EMAIL,
        RESET_PASSWORD
    }

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "local_account_id", nullable = false, updatable = false)
    private LocalAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Purpose purpose;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
