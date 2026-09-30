package org.letsemploy.ojobpub_publisher.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A tag's name after normalizing: the published schema caps it (spec 3.4, 9.6). */
record TagInput(
        @NotBlank(message = "{validation.name.required}")
        @Size(max = Tag.MAX_LENGTH, message = "{validation.tag.tooLong}")
        String name) {
}
