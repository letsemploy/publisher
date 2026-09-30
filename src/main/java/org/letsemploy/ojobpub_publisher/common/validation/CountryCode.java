package org.letsemploy.ojobpub_publisher.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An ISO 3166-1 alpha-2 code, in any case (spec 3.2). Null is not a country:
 * a location always has one, and the stored value is the published one.
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CountryCode.Validator.class)
public @interface CountryCode {

    String message() default "{validation.country.required}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<CountryCode, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            com.neovisionaries.i18n.CountryCode country =
                    com.neovisionaries.i18n.CountryCode.getByCodeIgnoreCase(value);
            return country != null && country != com.neovisionaries.i18n.CountryCode.UNDEFINED;
        }
    }
}
