package org.letsemploy.ojobpub_publisher.common.exception;

/**
 * A record does not exist, or the current user may not see it. Both cases answer
 * 404 so the existence of other employers' records is not disclosed (spec 2.4).
 *
 * <p>Unchecked: it is handled centrally by the error advice, and services should
 * not force every caller to declare it.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
