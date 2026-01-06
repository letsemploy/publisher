package org.letsemploy.ojobpub_publisher.web.view;

/**
 * The status a job presents in every list, detail and picker (spec 7.6).
 * Distinct from the stored lifecycle state: an ACTIVE job outside its date
 * window presents as EXPIRED, and one failing a publication requirement as
 * INCOMPLETE, so the UI can explain why it is not in a feed.
 */
public enum PublicationStatus {
    PUBLISHED, EXPIRED, INCOMPLETE, DRAFT, INACTIVE
}
