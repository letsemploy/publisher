package org.letsemploy.ojobpub_publisher.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

/** What a new token must name: itself, and at least one scope (spec 2.8, 9.6). */
record TokenInput(
        @NotBlank(message = "{validation.name.required}") String name,
        @NotEmpty(message = "{validation.scopes.required}") Set<TokenScope> scopes) {
}
