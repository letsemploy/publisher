package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

@Value
public class FeedFormView {
    String id;
    String name;
    String slug;
    String description;
    String urlPreview;
}
