package org.letsemploy.ojobpub_publisher.web.view;

public record TagRow(
        String id,
        String name,
        int jobCount,
        /** Shown only when the list covers more than one employer (spec 7.15). */
        String employerName) {
}
