package org.letsemploy.ojobpub_publisher.web.view;

public record LocationRow(
        String id,
        String city,
        String country,
        String countryName,
        int usageCount) {

    public boolean isDeletable() {
        return usageCount == 0;
    }
}
