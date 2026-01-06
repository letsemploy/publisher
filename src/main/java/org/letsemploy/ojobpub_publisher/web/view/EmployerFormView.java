package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

@Value
public class EmployerFormView {
    String id;
    String name;
    String slug;
    String url;
    String industry;
    String headquarters;
}
