package org.letsemploy.ojobpub_publisher.account;

import com.nulabinc.zxcvbn.Zxcvbn;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Component;

/**
 * What a new password must satisfy (spec 2.12), from
 * {@code app.local-accounts.password.*}: a length, optionally a mix of character
 * classes, and a minimum zxcvbn strength - an estimate of how many guesses it
 * takes, which knows common passwords, words, keyboard walks and dates, and is
 * told the person's own address so a password built from it scores low.
 *
 * <p>The same object states the rules on the forms and checks them, so the hint
 * a person reads before typing is the rule the password is held to.
 */
@Component
public class PasswordPolicy {

    private final LocalAccountProperties.Password rules;
    private final Zxcvbn zxcvbn = new Zxcvbn();

    public PasswordPolicy(LocalAccountProperties properties) {
        this.rules = properties.password();
    }

    /** The first rule the password breaks, as a message to show beside it; empty if it is acceptable. */
    public Optional<MessageSourceResolvable> refusal(String password, String email) {
        String candidate = password == null ? "" : password;
        if (candidate.length() < rules.minLength() || candidate.length() > rules.maxLength()) {
            return refuse("validation.password.length", rules.minLength(), rules.maxLength());
        }
        if (email != null && candidate.equalsIgnoreCase(email.trim())) {
            return refuse("validation.password.isEmail");
        }
        if (classes(candidate) < rules.requiredClasses()) {
            return refuse("validation.password.classes", rules.requiredClasses());
        }
        if (rules.minStrength() > 0 && zxcvbn.measure(candidate, inputs(email)).getScore() < rules.minStrength()) {
            return refuse("validation.password.weak");
        }
        return Optional.empty();
    }

    /** The rules as the forms state them, before anyone breaks one (spec 7.24). */
    public List<MessageSourceResolvable> hints() {
        List<MessageSourceResolvable> hints = new ArrayList<>();
        hints.add(message("account.password.hint.length", rules.minLength()));
        if (rules.requiredClasses() > 0) {
            hints.add(message("account.password.hint.classes", rules.requiredClasses()));
        }
        if (rules.minStrength() > 0) {
            hints.add(message("account.password.hint.strength"));
        }
        return hints;
    }

    /** Lower case, upper case, digits, and everything else. */
    static int classes(String password) {
        boolean lower = false, upper = false, digit = false, other = false;
        for (int cp : password.codePoints().toArray()) {
            if (Character.isLowerCase(cp)) {
                lower = true;
            } else if (Character.isUpperCase(cp)) {
                upper = true;
            } else if (Character.isDigit(cp)) {
                digit = true;
            } else {
                other = true;
            }
        }
        return (lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0) + (other ? 1 : 0);
    }

    /** The address and its parts, which a password should not be made of. */
    private static List<String> inputs(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        String address = email.trim().toLowerCase(Locale.ROOT);
        List<String> inputs = new ArrayList<>(List.of(address));
        inputs.addAll(List.of(address.split("[@._+-]")));
        return inputs;
    }

    private static Optional<MessageSourceResolvable> refuse(String code, Object... args) {
        return Optional.of(message(code, args));
    }

    private static MessageSourceResolvable message(String code, Object... args) {
        return new DefaultMessageSourceResolvable(new String[]{code}, args);
    }
}
