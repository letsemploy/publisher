package org.letsemploy.ojobpub_publisher.web.view;

/** One sidebar destination. {@code active} is resolved on the server from the
 *  request path, never guessed in the browser (spec 7.3). */
public record NavItem(String icon, String labelKey, String href, boolean active) {
}
