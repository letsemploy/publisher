package org.letsemploy.ojobpub_publisher.web.view;

/** One publication requirement from spec 4.3, with the field that fixes it. */
public record ReadinessCheck(String labelKey, boolean satisfied, String fixAnchor) {
}
