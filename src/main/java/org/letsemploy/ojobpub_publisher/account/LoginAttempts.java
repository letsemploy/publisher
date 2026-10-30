package org.letsemploy.ojobpub_publisher.account;

import java.time.Clock;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Failed sign-ins of local accounts, per address, per client and per network,
 * over the configured window (spec 2.12). Two thresholds read them:
 *
 * <ul>
 *   <li><b>a captcha</b> once either has {@code captcha-after-failures}: guessing
 *       then costs a solved captcha per try, and the account's owner can still
 *       sign in;</li>
 *   <li><b>a refusal</b> of every attempt once the address has
 *       {@code max-attempts}, the client five times that, or its network
 *       {@link Throttle#CLIENTS_PER_NETWORK} times the client's - the backstop
 *       where no captcha is configured.</li>
 * </ul>
 *
 * <p>The network's limit is what keeps one rented IPv6 block from filling the
 * other two: rotating through its /64s, every failure still counts against its
 * /48, so it opens only so many client and address keys per window.
 *
 * <p>The throttles are bounded, and full of running windows they count nothing
 * new (see {@link Throttle}). A sign-in they cannot count is {@link #uncounted}:
 * it takes a captcha on every try, or is refused where there is none, because a
 * count it never got must not let it off more lightly than one it did.
 *
 * <p>Neither says anything about an account: an unknown address collects failures
 * exactly like a known one. In memory and per instance, like the other limits.
 */
@Component
public class LoginAttempts {

    private final Throttle perAddress;
    private final Throttle perClient;
    private final Throttle perNetwork;
    private final int captchaAfter;

    @Autowired
    public LoginAttempts(LocalAccountProperties properties) {
        this(properties, Throttle.MAX_KEYS);
    }

    /** With another cap on each throttle: tests fill one without a hundred thousand keys. */
    LoginAttempts(LocalAccountProperties properties, int maxKeys) {
        Clock clock = Clock.systemUTC();
        this.perAddress = new Throttle(properties.maxAttempts(), properties.throttleWindow(), clock, maxKeys);
        // A client may try several addresses; allow it a few people's worth.
        this.perClient = new Throttle(properties.maxAttempts() * 5, properties.throttleWindow(), clock, maxKeys);
        this.perNetwork = new Throttle(properties.maxAttempts() * 5 * Throttle.CLIENTS_PER_NETWORK,
                properties.throttleWindow(), clock, maxKeys);
        this.captchaAfter = properties.captchaAfterFailures();
    }

    /** Whether this attempt goes uncounted, by any of the throttles, for want of room. */
    boolean uncounted(String address, String client) {
        return perAddress.uncounted(key(address)) || perClient.uncounted(Throttle.clientKey(client))
                || perNetwork.uncounted(Throttle.networkKey(client));
    }

    boolean refused(String address, String client) {
        return perAddress.exhausted(key(address)) || perClient.exhausted(Throttle.clientKey(client))
                || perNetwork.exhausted(Throttle.networkKey(client));
    }

    /** Whether an attempt for this address, from this client, must carry a solved captcha. */
    boolean captchaRequired(String address, String client) {
        return captchaAfter == 0 || perAddress.count(key(address)) >= captchaAfter
                || perClient.count(Throttle.clientKey(client)) >= captchaAfter
                || uncounted(address, client);
    }

    /** Whether the sign-in page should show the captcha to this client before any address is typed. */
    public boolean captchaRequired(String client) {
        // No address is typed yet, so a full per-address throttle shows it to everyone.
        return captchaAfter == 0 || perClient.count(Throttle.clientKey(client)) >= captchaAfter
                || perClient.uncounted(Throttle.clientKey(client))
                || perNetwork.uncounted(Throttle.networkKey(client)) || perAddress.full();
    }

    void failed(String address, String client) {
        perAddress.record(key(address));
        perClient.record(Throttle.clientKey(client));
        perNetwork.record(Throttle.networkKey(client));
    }

    /** A success forgets the address's failures; the client's stay, since it may be trying others. */
    void succeeded(String address) {
        perAddress.clear(key(address));
    }

    private static String key(String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    }
}
