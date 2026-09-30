package org.letsemploy.ojobpub_publisher.web.view;

public record FeedRow(
        String id,
        String name,
        String slug,
        String publicUrl,
        int publishedCount,
        int memberCount,
        String lastChanged,
        int excludedCount) {
}
