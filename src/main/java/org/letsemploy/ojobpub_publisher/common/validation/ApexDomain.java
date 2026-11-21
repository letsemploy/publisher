package org.letsemploy.ojobpub_publisher.common.validation;

import com.google.common.net.InternetDomainName;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;

/**
 * A registrable domain itself - {@code acme.ch}, {@code acme.co.uk} - by the
 * Public Suffix List, never a subdomain such as {@code www.acme.ch} (spec 3.13).
 * Null is valid, and so is a value that is no host name at all: the field's
 * {@code @Pattern} reports that, so one field gets one message.
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ApexDomain.Validator.class)
public @interface ApexDomain {

    String message() default "{validation.host.apex}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ApexDomain, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || !InternetDomainName.isValid(value)) {
                return true;
            }
            return InternetDomainName.from(value).isTopPrivateDomain();
        }

        /** The registrable domain of a host, for offering one: {@code www.acme.ch} gives {@code acme.ch}. */
        public static Optional<String> registrable(String host) {
            if (host == null || !InternetDomainName.isValid(host)) {
                return Optional.empty();
            }
            InternetDomainName name = InternetDomainName.from(host);
            return name.isUnderPublicSuffix() ? Optional.of(name.topPrivateDomain().toString()) : Optional.empty();
        }
    }
}
