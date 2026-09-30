package org.letsemploy.ojobpub_publisher.web.view;

public record EmployerRow(
        String id,
        String name,
        String slug,
        String industry,
        String headquarters,
        int jobCount,
        int feedCount) {
}
