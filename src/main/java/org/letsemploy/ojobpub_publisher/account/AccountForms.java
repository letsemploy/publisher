package org.letsemploy.ojobpub_publisher.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The account screens' forms (spec 7.24), bound by the controller and checked by the service (spec 9.6). */
final class AccountForms {

    private AccountForms() {
    }

    /** Sign-up asks for no password: the mailed link does (spec 2.12). */
    record SignUp(
            @NotBlank(message = "{validation.email.format}")
            @Pattern(regexp = "(?s)[^@\\s]+@[^@\\s]+", message = "{validation.email.format}")
            @Size(max = 255, message = "{validation.email.format}")
            String email,
            @NotBlank(message = "{validation.name.required}")
            @Size(max = 255, message = "{validation.name.tooLong}")
            String name) {
    }

    /** Resending the link and a forgotten password both take an address alone. */
    record Address(
            @NotBlank(message = "{validation.email.format}")
            @Pattern(regexp = "(?s)[^@\\s]+@[^@\\s]+", message = "{validation.email.format}")
            @Size(max = 255, message = "{validation.email.format}")
            String email) {
    }

    /** Choosing a password from a mailed link: completing a sign-up, or a reset. */
    @NewPassword
    record ChoosePassword(String token, String password, String passwordRepeat, String email)
            implements NewPassword.Form {
    }

    /** Changing one's own password while signed in. */
    @NewPassword
    record ChangePassword(
            @NotBlank(message = "{validation.password.current}") String current,
            String password,
            String passwordRepeat,
            String email) implements NewPassword.Form {
    }
}
