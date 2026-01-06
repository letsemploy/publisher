package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

/** One sidebar destination. {@code active} is resolved on the server from the
 *  request path, never guessed in the browser (spec 7.3). */
@Value
public class NavItem {
    String icon;
    String labelKey;
    String href;
    boolean active;
}
