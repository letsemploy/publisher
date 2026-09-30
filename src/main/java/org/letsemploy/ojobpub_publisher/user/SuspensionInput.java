package org.letsemploy.ojobpub_publisher.user;

import jakarta.validation.constraints.Size;

/** The optional reason for suspending an account, as it is stored (spec 2.11, 9.6). */
record SuspensionInput(@Size(max = UserService.MAX_REASON, message = "{validation.reason.tooLong}") String reason) {
}
