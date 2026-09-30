package org.letsemploy.ojobpub_publisher.web.view;

/** A job's presence in a feed, with why it is or is not published (spec 7.12). */
public record FeedMembership(String feedId, String feedName, PublicationStatus status, String reasonKey) {
}
