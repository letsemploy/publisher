package org.letsemploy.ojobpub_publisher.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.common.Base;

/**
 * An email-and-password account (spec 2.12). It signs in as the {@code users}
 * row with issuer {@link #ISSUER} and this id as subject, which is why the id -
 * never the email - is what identifies it.
 *
 * <p>Pending until the link mailed to its address is followed: until then it has
 * no password, so it cannot sign in, and no {@code users} row.
 */
@Entity
@Table(name = "local_accounts")
@Getter
@Setter
public class LocalAccount extends Base {

    /** The issuer of every local account in the users table (spec 2.2, 3.7). */
    public static final String ISSUER = "local";

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** An adaptive hash, never the password; null while pending. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** Sessions that began before this end at their next request (spec 2.12). */
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    public boolean isVerified() {
        return verifiedAt != null;
    }
}
