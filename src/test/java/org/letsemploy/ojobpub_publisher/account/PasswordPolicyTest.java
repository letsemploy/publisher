package org.letsemploy.ojobpub_publisher.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSourceResolvable;

/** The configurable password policy of spec 2.12; no Spring, no database. */
class PasswordPolicyTest {

    private static final String EMAIL = "ada.lovelace@example.com";

    @Test
    void theDefaultsAskForLengthAndAPasswordThatIsHardToGuess() {
        PasswordPolicy policy = policy(12, 128, 0, 3);
        assertThat(code(policy, "short")).isEqualTo("validation.password.length");
        assertThat(code(policy, "password1234")).isEqualTo("validation.password.weak");
        assertThat(code(policy, "qwertyuiop123")).isEqualTo("validation.password.weak");
        assertThat(code(policy, EMAIL)).isEqualTo("validation.password.isEmail");
        assertThat(code(policy, "lovelace2024!")).as("built from the address").isEqualTo("validation.password.weak");
        assertThat(policy.refusal("marmot violin tuesday gravel", EMAIL)).isEmpty();
    }

    @Test
    void characterClassesAreRequiredOnlyWhenConfigured() {
        PasswordPolicy classes = policy(12, 128, 3, 0);
        assertThat(code(classes, "alllowercaseletters")).isEqualTo("validation.password.classes");
        assertThat(classes.refusal("Lower and UPPER 42", EMAIL)).isEmpty();
        assertThat(PasswordPolicy.classes("aA1!")).isEqualTo(4);
        assertThat(PasswordPolicy.classes("äÖ")).as("letters beyond ASCII count").isEqualTo(2);
    }

    @Test
    void strengthZeroChecksNothingButLength() {
        assertThat(policy(8, 128, 0, 0).refusal("password", EMAIL)).isEmpty();
    }

    @Test
    void theMaximumHolds() {
        assertThat(code(policy(12, 20, 0, 0), "a".repeat(21))).isEqualTo("validation.password.length");
    }

    @Test
    void theHintsStateExactlyTheRulesInForce() {
        assertThat(policy(12, 128, 0, 0).hints()).extracting(h -> h.getCodes()[0])
                .containsExactly("account.password.hint.length");
        assertThat(policy(12, 128, 2, 3).hints()).extracting(h -> h.getCodes()[0])
                .containsExactly("account.password.hint.length", "account.password.hint.classes",
                        "account.password.hint.strength");
    }

    @Test
    void aNonsensicalPolicyStopsTheApplicationFromStarting() {
        assertThatThrownBy(() -> new LocalAccountProperties.Password(12, 8, 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalAccountProperties.Password(12, 128, 5, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LocalAccountProperties.Password(12, 128, 0, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String code(PasswordPolicy policy, String password) {
        return policy.refusal(password, EMAIL).map(MessageSourceResolvable::getCodes).map(c -> c[0]).orElse(null);
    }

    private static PasswordPolicy policy(int min, int max, int classes, int strength) {
        return new PasswordPolicy(new LocalAccountProperties(true, 10, 3, 10, Duration.ofMinutes(15),
                new LocalAccountProperties.Password(min, max, classes, strength)));
    }
}
