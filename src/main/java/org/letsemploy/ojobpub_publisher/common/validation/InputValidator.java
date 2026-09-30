package org.letsemploy.ojobpub_publisher.common.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.springframework.stereotype.Component;

/**
 * Checks the shape of a service's input with Bean Validation (spec 9.6) and
 * answers in field errors, like every other refusal (spec 8.2).
 *
 * <p>Services call it, not controllers, so the form and the management API
 * cannot validate differently. It never lets a {@code ConstraintViolationException}
 * out: a violation is a {@link ValidationFailure} keyed by the property, which is
 * the name of the field the user typed into and what {@code ApiErrors} maps.
 * Messages are bundle keys, interpolated in the request's locale (spec 8.1).
 */
@Component
public class InputValidator {

    private final Validator validator;

    public InputValidator(Validator validator) {
        this.validator = validator;
    }

    /** Field errors in a stable order, empty when the input is well-formed. */
    public Map<String, String> violations(Object input) {
        Map<String, String> errors = new LinkedHashMap<>();
        validator.validate(input).stream()
                .sorted((a, b) -> a.getPropertyPath().toString().compareTo(b.getPropertyPath().toString()))
                .forEach(v -> errors.putIfAbsent(field(v), v.getMessage()));
        return errors;
    }

    /** Refuses a malformed input with its field errors. */
    public void check(Object input) {
        Map<String, String> errors = violations(input);
        if (!errors.isEmpty()) {
            throw new ValidationFailure(errors);
        }
    }

    private static String field(ConstraintViolation<?> violation) {
        return violation.getPropertyPath().toString();
    }
}
