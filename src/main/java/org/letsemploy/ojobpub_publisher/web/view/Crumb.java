package org.letsemploy.ojobpub_publisher.web.view;

/** One breadcrumb entry. A null href marks the current, non-linked page. */
public record Crumb(String label, String href) {
}
