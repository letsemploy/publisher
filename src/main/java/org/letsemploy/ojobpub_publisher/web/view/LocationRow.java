package org.letsemploy.ojobpub_publisher.web.view;

import lombok.Value;

@Value
public class LocationRow {
    String id;
    String city;
    String country;
    String countryName;
    int usageCount;

    public boolean isDeletable() {
        return usageCount == 0;
    }
}
