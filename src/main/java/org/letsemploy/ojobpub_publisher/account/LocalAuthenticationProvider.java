package org.letsemploy.ojobpub_publisher.account;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Signs a local account in with its email and password (spec 2.12).
 *
 * <p>Every failure is the same {@link BadCredentialsException}: an unknown
 * address, a wrong password, a pending account (it has no password yet) and a
 * throttled attempt. Spring checks a password against a dummy hash when the
 * address is unknown, so the time taken says nothing either.
 *
 * <p>Not a bean, like the security filters: a {@code UserDetailsService} or
 * provider bean would also be picked up for a global authentication manager
 * with Spring's default checks. The back-office chain constructs it.
 */
public final class LocalAuthenticationProvider extends DaoAuthenticationProvider {

    private final Throttle perAddress;
    private final Throttle perClient;

    public LocalAuthenticationProvider(LocalAccountRepo accounts, PasswordEncoder passwordEncoder,
                                       LocalAccountProperties properties) {
        super(details(accounts));
        setPasswordEncoder(passwordEncoder);
        this.perAddress = new Throttle(properties.maxAttempts(), properties.throttleWindow(), Clock.systemUTC());
        // A client may try several addresses; allow it a few people's worth.
        this.perClient = new Throttle(properties.maxAttempts() * 5, properties.throttleWindow(),
                Clock.systemUTC());
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String address = authentication.getName() == null ? "" : authentication.getName().trim().toLowerCase(Locale.ROOT);
        String client = authentication.getDetails() instanceof WebAuthenticationDetails web
                ? web.getRemoteAddress() : "unknown";
        if (perAddress.exhausted(address) || perClient.exhausted(client)) {
            throw new BadCredentialsException("Throttled");
        }
        try {
            return super.authenticate(authentication);
        } catch (AuthenticationException e) {
            perAddress.record(address);
            perClient.record(client);
            throw new BadCredentialsException("Sign-in failed");
        }
    }

    private static UserDetailsService details(LocalAccountRepo accounts) {
        return email -> accounts.findByEmailIgnoreCase(email.trim())
                // Pending: no password yet, so nothing can match it.
                .filter(account -> account.getPasswordHash() != null)
                .<UserDetails>map(account -> new LocalUser(account.getId(), account.getEmail(),
                        account.getDisplayName(), account.getPasswordHash(), Instant.now()))
                .orElseThrow(() -> new UsernameNotFoundException("No such account"));
    }
}
