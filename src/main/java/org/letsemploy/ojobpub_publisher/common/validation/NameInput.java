package org.letsemploy.ojobpub_publisher.common.validation;

import jakarta.validation.constraints.NotBlank;

/** A record's name, which every named thing requires (spec 9.6). */
public record NameInput(@NotBlank(message = "{validation.name.required}") String name) {
}
