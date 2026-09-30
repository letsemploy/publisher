package org.letsemploy.ojobpub_publisher.web.view;

/** A member job omitted at serving time (spec 5.3) - surfaced, never only logged. */
public record FeedExclusion(String jobId, String jobTitle, String reasonKey) {
}
