package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

@Value
public class EmployerRow {
    String id;
    String name;
    String slug;
    String industry;
    String headquarters;
    int jobCount;
    int feedCount;
}
