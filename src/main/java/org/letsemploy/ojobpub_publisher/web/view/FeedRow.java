package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

@Value
public class FeedRow {
    String id;
    String name;
    String slug;
    String publicUrl;
    int publishedCount;
    int memberCount;
    String lastChanged;
    int excludedCount;
}
