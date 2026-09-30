package org.letsemploy.ojobpub_publisher.account;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * A new password that satisfies the configured policy (spec 2.12) and was typed
 * the same twice. The validator is a Spring bean, as Bean Validation creates
 * validators through Spring here, so it can hold the policy.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NewPassword.Validator.class)
@interface NewPassword {

    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** A form that sets a password. */
    interface Form {
        String password();

        String passwordRepeat();

        /** The account's address, which the password must not be made of; null if not known here. */
        String email();
    }

    class Validator implements ConstraintValidator<NewPassword, Form> {

        private final PasswordPolicy policy;
        private final MessageSource messages;

        Validator(PasswordPolicy policy, MessageSource messages) {
            this.policy = policy;
            this.messages = messages;
        }

        @Override
        public boolean isValid(Form form, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();
            var refusal = policy.refusal(form.password(), form.email());
            if (refusal.isPresent()) {
                // Resolved here rather than as a {template}: the arguments are the policy's.
                // Escaped, so the validator reads it as text and not as a template.
                String text = messages.getMessage(refusal.get(), LocaleContextHolder.getLocale());
                context.buildConstraintViolationWithTemplate(escape(text))
                        .addPropertyNode("password").addConstraintViolation();
                return false;
            }
            if (!form.password().equals(form.passwordRepeat())) {
                context.buildConstraintViolationWithTemplate("{validation.password.mismatch}")
                        .addPropertyNode("passwordRepeat").addConstraintViolation();
                return false;
            }
            return true;
        }

        private static String escape(String text) {
            return text.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("$", "\\$");
        }
    }
}
