package org.letsemploy.ojobpub_publisher.account;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.local-accounts.*} (spec 2.12, 9.5). Off unless configured.
 *
 * @param maxAttempts sign-in failures allowed per address, and per client, in one window
 * @param maxMails    sign-up, resend and forgot requests allowed per client in one window
 */
@ConfigurationProperties("app.local-accounts")
public record LocalAccountProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("12") int minPasswordLength,
        @DefaultValue("10") int maxAttempts,
        @DefaultValue("10") int maxMails,
        @DefaultValue("PT15M") Duration throttleWindow) {
}
