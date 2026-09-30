package org.letsemploy.ojobpub_publisher.account;

import java.time.Instant;
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
 * <p>In order, and each before the password is looked at: an address or client
 * past its limit is refused; one past the captcha threshold must bring a solved
 * captcha ({@link CaptchaRequiredException}). Then every failure is the same
 * {@link BadCredentialsException} - an unknown address, a wrong password, a
 * pending account (it has no password yet). Spring checks a password against a
 * dummy hash when the address is unknown, so the time taken says nothing either.
 *
 * <p>Not a bean, like the security filters: a {@code UserDetailsService} or
 * provider bean would also be picked up for a global authentication manager
 * with Spring's default checks. The back-office chain constructs it.
 */
public final class LocalAuthenticationProvider extends DaoAuthenticationProvider {

    private final LoginAttempts attempts;
    private final CaptchaCheck captcha;

    public LocalAuthenticationProvider(LocalAccountRepo accounts, PasswordEncoder passwordEncoder,
                                       LoginAttempts attempts, CaptchaCheck captcha) {
        super(details(accounts));
        setPasswordEncoder(passwordEncoder);
        this.attempts = attempts;
        this.captcha = captcha;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String address = authentication.getName() == null ? "" : authentication.getName();
        String client = authentication.getDetails() instanceof WebAuthenticationDetails web
                ? web.getRemoteAddress() : "unknown";
        if (attempts.refused(address, client)) {
            throw new BadCredentialsException("Throttled");
        }
        if (captcha.configured() && attempts.captchaRequired(address, client)) {
            String token = authentication.getDetails() instanceof LoginDetails login ? login.getCaptchaToken() : null;
            if (!captcha.solved(token, client)) {
                throw new CaptchaRequiredException();
            }
        }
        try {
            Authentication result = super.authenticate(authentication);
            attempts.succeeded(address);
            return result;
        } catch (AuthenticationException e) {
            attempts.failed(address, client);
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
