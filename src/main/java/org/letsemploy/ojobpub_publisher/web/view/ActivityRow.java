package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

/** One line of an audit log (spec 7.21): when, who, what, and in which employer. */
@Value
public class ActivityRow {
    String at;
    String actor;
    /** "user", "token" or "system", so a token is never mistaken for a person. */
    String actorKind;
    String text;
    /** Null for an event about a person rather than an employer. */
    String employer;
}
