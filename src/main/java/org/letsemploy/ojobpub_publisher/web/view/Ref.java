package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

/** An identifier plus its human label: select options, chips, breadcrumb targets. */
@Value
public class Ref {
    String id;
    String label;
}
