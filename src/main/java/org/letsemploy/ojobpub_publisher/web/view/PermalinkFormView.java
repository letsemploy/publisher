package org.letsemploy.ojobpub_publisher.web.view;

public record PermalinkFormView(
        String id,
        String name,
        String description,
        /** Empty for none. */
        String feedId,
        /** The URL, once there is one: a new permalink gets its id on saving. */
        String publicUrl) {
}
