package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

@Value
public class JobFilterView {
    String q;
    String status;
    String jobType;
    String sort;
}
