package org.letsemploy.ojobpub_publisher.account;

import org.springframework.security.core.AuthenticationException;

/**
 * A sign-in that needed a solved captcha and came without one (spec 2.12). Raised
 * before the password is looked at, so it says nothing about it.
 */
public final class CaptchaRequiredException extends AuthenticationException {

    public CaptchaRequiredException() {
        super("A solved captcha is required");
    }
}
