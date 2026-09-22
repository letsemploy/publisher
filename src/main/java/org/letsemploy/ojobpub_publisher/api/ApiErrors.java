package org.letsemploy.ojobpub_publisher.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Turns a {@link ValidationFailure} into the {@code userErrors} of a payload
 * (spec 11.4).
 *
 * <p>Two things happen here. The {@code code} is made stable so a client can
 * branch on it instead of matching message text that changes with the language.
 * And the {@code message} is resolved through the same bundles the screens use
 * (spec 8.1), so the API and the UI cannot disagree about what is wrong.
 *
 * <p>The {@code field} is the name of the input field that fixes it, which is not
 * always what the service names: activation is refused by publication
 * requirements (spec 4.3), whose keys describe a readiness row on a screen rather
 * than an argument of {@code JobInput}.
 */
@Component
@RequiredArgsConstructor
public class ApiErrors {

    private final MessageSource messages;

    public List<UserError> from(ValidationFailure failure) {
        return failure.getFieldErrors().entrySet().stream()
                .map(e -> new UserError(field(e.getKey()), message(e.getKey(), e.getValue()),
                        code(e.getKey())))
                .toList();
    }

    /** A readiness key names a requirement, not an argument; translate where one exists. */
    private String field(String key) {
        return switch (key) {
            case "readiness.locations" -> "locationIds";
            case "readiness.salary" -> "salaryCurrency";
            // "Title, URL, language and job type are present" is about four fields
            // at once, so naming one of them would be a guess.
            case "readiness.required", "readiness.window", "readiness.status",
                 "readiness.publishedAt" -> null;
            default -> key;
        };
    }

    private String message(String key, String fallback) {
        if (!key.startsWith("readiness.")) {
            return fallback;
        }
        // The readiness rows say what is required; the service's own text only
        // says that something is.
        return messages.getMessage(key, null, fallback, LocaleContextHolder.getLocale());
    }

    private String code(String key) {
        if (key.startsWith("readiness.")) {
            return "NOT_READY";
        }
        return switch (key) {
            case "role", "member" -> "LAST_OWNER";
            case "status" -> "INVALID_TRANSITION";
            case "email" -> "INVALID_EMAIL";
            default -> "INVALID_INPUT";
        };
    }
}
