package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

/** One publication requirement from spec 4.3, with the field that fixes it. */
@Value
public class ReadinessCheck {
    String labelKey;
    boolean satisfied;
    String fixAnchor;
}
