package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

/** A member job omitted at serving time (spec 5.3) - surfaced, never only logged. */
@Value
public class FeedExclusion {
    String jobId;
    String jobTitle;
    String reasonKey;
}
