package org.letsemploy.ojobpub_publisher.account;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

/**
 * The password rule of spec 2.12: long enough, not too long, typed the same
 * twice, and not the email. No rules on character classes. The minimum is
 * configured, so the validator is a Spring bean reading it.
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

        /** The account's address, which the password must not be; null if not known here. */
        String email();
    }

    int MAX_LENGTH = 128;

    class Validator implements ConstraintValidator<NewPassword, Form> {

        private final int minLength;

        Validator(LocalAccountProperties properties) {
            this.minLength = properties.minPasswordLength();
        }

        @Override
        public boolean isValid(Form form, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();
            String password = form.password() == null ? "" : form.password();
            if (password.length() < minLength || password.length() > MAX_LENGTH) {
                context.unwrap(HibernateConstraintValidatorContext.class)
                        .addMessageParameter("min", minLength)
                        .addMessageParameter("max", MAX_LENGTH)
                        .buildConstraintViolationWithTemplate("{validation.password.length}")
                        .addPropertyNode("password").addConstraintViolation();
                return false;
            }
            if (form.email() != null && password.equalsIgnoreCase(form.email().trim())) {
                context.buildConstraintViolationWithTemplate("{validation.password.isEmail}")
                        .addPropertyNode("password").addConstraintViolation();
                return false;
            }
            if (!password.equals(form.passwordRepeat())) {
                context.buildConstraintViolationWithTemplate("{validation.password.mismatch}")
                        .addPropertyNode("passwordRepeat").addConstraintViolation();
                return false;
            }
            return true;
        }
    }
}
