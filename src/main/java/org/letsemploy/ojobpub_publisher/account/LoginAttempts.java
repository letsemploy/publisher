package org.letsemploy.ojobpub_publisher.account;

import java.time.Clock;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Failed sign-ins of local accounts, per address and per client, over the
 * configured window (spec 2.12). Two thresholds read them:
 *
 * <ul>
 *   <li><b>a captcha</b> once either has {@code captcha-after-failures}: guessing
 *       then costs a solved captcha per try, and the account's owner can still
 *       sign in;</li>
 *   <li><b>a refusal</b> of every attempt once the address has
 *       {@code max-attempts}, or the client five times that - the backstop where
 *       no captcha is configured.</li>
 * </ul>
 *
 * <p>Neither says anything about an account: an unknown address collects failures
 * exactly like a known one. In memory and per instance, like the other limits.
 */
@Component
public class LoginAttempts {

    private final Throttle perAddress;
    private final Throttle perClient;
    private final int captchaAfter;

    public LoginAttempts(LocalAccountProperties properties) {
        this.perAddress = new Throttle(properties.maxAttempts(), properties.throttleWindow(), Clock.systemUTC());
        // A client may try several addresses; allow it a few people's worth.
        this.perClient = new Throttle(properties.maxAttempts() * 5, properties.throttleWindow(), Clock.systemUTC());
        this.captchaAfter = properties.captchaAfterFailures();
    }

    boolean refused(String address, String client) {
        return perAddress.exhausted(key(address)) || perClient.exhausted(client);
    }

    /** Whether an attempt for this address, from this client, must carry a solved captcha. */
    boolean captchaRequired(String address, String client) {
        return captchaAfter == 0 || perAddress.count(key(address)) >= captchaAfter
                || perClient.count(client) >= captchaAfter;
    }

    /** Whether the sign-in page should show the captcha to this client before any address is typed. */
    public boolean captchaRequired(String client) {
        return captchaAfter == 0 || perClient.count(client) >= captchaAfter;
    }

    void failed(String address, String client) {
        perAddress.record(key(address));
        perClient.record(client);
    }

    /** A success forgets the address's failures; the client's stay, since it may be trying others. */
    void succeeded(String address) {
        perAddress.clear(key(address));
    }

    private static String key(String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    }
}
