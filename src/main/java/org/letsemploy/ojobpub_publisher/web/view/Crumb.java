package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

/** One breadcrumb entry. A null href marks the current, non-linked page. */
@Value
public class Crumb {
    String label;
    String href;
}
