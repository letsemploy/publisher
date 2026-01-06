package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;
import lombok.Value;

@Value
public class FeedDetailView {
    String id;
    String name;
    String slug;
    String description;
    String publicUrl;
    List<JobRow> members;
    List<JobRow> candidates;
    List<FeedExclusion> exclusions;
    String jsonPreview;
    boolean schemaValid;

    public long getPublishedCount() {
        return members.stream().filter(j -> j.getStatus() == PublicationStatus.PUBLISHED).count();
    }
}
