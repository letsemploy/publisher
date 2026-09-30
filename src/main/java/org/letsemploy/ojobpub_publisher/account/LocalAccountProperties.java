package org.letsemploy.ojobpub_publisher.account;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.local-accounts.*} (spec 2.12, 9.5). Off unless configured.
 *
 * @param maxAttempts          failed sign-ins per address, and five times that per client, in one
 *                             window before every further attempt is refused; 0 never refuses
 * @param captchaAfterFailures failed sign-ins per address, or per client, in one window before
 *                             signing in also takes a solved captcha; 0 always takes one. Applies
 *                             only when a captcha is configured
 * @param maxMails             sign-up, resend and forgot requests allowed per client in one window
 * @param password             what a new password must satisfy
 */
@ConfigurationProperties("app.local-accounts")
public record LocalAccountProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("10") int maxAttempts,
        @DefaultValue("3") int captchaAfterFailures,
        @DefaultValue("10") int maxMails,
        @DefaultValue("PT15M") Duration throttleWindow,
        @DefaultValue Password password) {

    /**
     * The password policy (spec 2.12).
     *
     * @param minLength       fewest characters
     * @param maxLength       most characters; an adaptive hash reads only so many
     * @param requiredClasses how many of lower case, upper case, digits and symbols it must mix, 0-4
     * @param minStrength     the lowest zxcvbn score accepted, 0-4: 0 checks nothing, 2 refuses the
     *                        guessable, 3 asks for what resists an offline attack, 4 for very strong
     */
    public record Password(
            @DefaultValue("12") int minLength,
            @DefaultValue("128") int maxLength,
            @DefaultValue("0") int requiredClasses,
            @DefaultValue("3") int minStrength) {

        public Password {
            if (minLength < 1 || maxLength < minLength) {
                throw new IllegalArgumentException("app.local-accounts.password: need 1 <= min-length <= max-length");
            }
            if (requiredClasses < 0 || requiredClasses > 4) {
                throw new IllegalArgumentException("app.local-accounts.password.required-classes: 0 to 4");
            }
            if (minStrength < 0 || minStrength > 4) {
                throw new IllegalArgumentException("app.local-accounts.password.min-strength: 0 to 4");
            }
        }
    }
}
