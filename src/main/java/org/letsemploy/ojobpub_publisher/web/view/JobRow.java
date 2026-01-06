package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;
import lombok.Value;

@Value
public class JobRow {
    String id;
    String title;
    PublicationStatus status;
    String jobType;
    List<String> locations;
    int feedCount;
    String referenceId;
}
