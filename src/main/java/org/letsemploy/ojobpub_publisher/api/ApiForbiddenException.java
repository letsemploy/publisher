package org.letsemploy.ojobpub_publisher.api;

/**
 * The request authenticated but the token may not do this (spec 11.2).
 *
 * <p>Deliberately not the back-office's 404-not-403: hiding existence from a
 * human browsing is worth the confusion, but an integration needs to know
 * whether to fix its credentials or stop retrying.
 */
public class ApiForbiddenException extends RuntimeException {
    public ApiForbiddenException(String message) {
        super(message);
    }
}
