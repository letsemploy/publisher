package org.letsemploy.ojobpub_publisher.common.exception;

import java.util.List;
import java.util.Map;

/** Field-level validation failures, carried back to the form (spec 8.2). */
public class ValidationFailure extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public ValidationFailure(Map<String, String> fieldErrors) {
        super("Validation failed: " + String.join(", ", fieldErrors.keySet()));
        this.fieldErrors = fieldErrors;
    }

    public ValidationFailure(String field, String message) {
        this(Map.of(field, message));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public List<String> fields() {
        return List.copyOf(fieldErrors.keySet());
    }
}
