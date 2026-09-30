package org.letsemploy.ojobpub_publisher.account;

import java.io.Serial;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Someone signed in with a local account (spec 2.12): the principal of the
 * session. Authorities are empty on purpose - what a person may do is decided in
 * the services from their role and memberships (spec 2.4), as for any provider.
 *
 * <p>It carries when it signed in, so a session older than the account's last
 * password change can be told apart and ended.
 */
public final class LocalUser implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final String displayName;
    private String passwordHash;
    private final Instant signedInAt;

    public LocalUser(UUID id, String email, String displayName, String passwordHash, Instant signedInAt) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.signedInAt = signedInAt;
    }

    /** The same person, signed in again now - after they changed their own password. */
    public LocalUser renewedAt(Instant when) {
        return new LocalUser(id, email, displayName, null, when);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getSignedInAt() {
        return signedInAt;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
