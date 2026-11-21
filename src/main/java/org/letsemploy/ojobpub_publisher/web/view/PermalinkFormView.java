package org.letsemploy.ojobpub_publisher.web.view;

public record PermalinkFormView(
        String id,
        String name,
        String description,
        /** Empty for none. */
        String feedId,
        /** The employer's website, as typed or saved; null for none. */
        String websiteHost,
        /** The URL, once there is one: a new permalink gets its id on saving. */
        String publicUrl,
        /** Where the website serves it, once a host is saved. */
        String websiteUrl,
        /** The last check's result as its name, or null when never checked. */
        String checkResult,
        boolean checkWorks,
        /** When it was checked, formatted. */
        String checkedAt) {
}
