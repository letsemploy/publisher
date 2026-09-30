package org.letsemploy.ojobpub_publisher.web.view;

/** One status change, for the audit trail spec 10 requires to be visible. */
public record TransitionEvent(String from, String to, String actor, String at) {
}
