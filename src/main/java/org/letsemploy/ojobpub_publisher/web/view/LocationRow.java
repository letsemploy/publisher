package org.letsemploy.ojobpub_publisher.web.view;

public record LocationRow(
        String id,
        String city,
        String country,
        String countryName,
        int usageCount,
        /** Shown only when the list covers more than one employer (spec 7.14). */
        String employerName) {

    public boolean isDeletable() {
        return usageCount == 0;
    }
}
