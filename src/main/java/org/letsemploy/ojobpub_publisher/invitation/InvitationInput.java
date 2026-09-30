package org.letsemploy.ojobpub_publisher.invitation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * The address to invite (spec 2.6). Deliberately loose - anything with an
 * {@code @} - because it is only ever compared with the addresses accounts hold.
 */
record InvitationInput(
        @NotBlank(message = "{validation.email.required}")
        @Pattern(regexp = "(?s).*@.*", message = "{validation.email.required}")
        String email) {
}
