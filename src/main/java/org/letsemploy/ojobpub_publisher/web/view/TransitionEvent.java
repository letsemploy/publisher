package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

/** One status change, for the audit trail spec 10 requires to be visible. */
@Value
public class TransitionEvent {
    String from;
    String to;
    String actor;
    String at;
}
