package org.letsemploy.ojobpub_publisher.web.view;

/** One line of an audit log (spec 7.21): when, who, what, and in which employer. */
public record ActivityRow(
        String at,
        String actor,
        /** "user", "token" or "system", so a token is never mistaken for a person. */
        String actorKind,
        String text,
        /** Null for an event about a person rather than an employer. */
        String employer) {
}
