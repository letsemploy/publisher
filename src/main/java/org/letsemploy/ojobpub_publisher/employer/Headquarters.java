package org.letsemploy.ojobpub_publisher.employer;

import java.util.UUID;

/**
 * Where an employer is headquartered, as its form or the API gives it (spec 3.1):
 * one of its own locations by id, or a new city and country, which joins its
 * locations. A new employer has no locations yet, so it can only give the latter.
 */
public record Headquarters(UUID locationId, String newCity, String newCountry) {

    public static Headquarters existing(UUID locationId) {
        return new Headquarters(locationId, null, null);
    }

    public static Headquarters newLocation(String city, String country) {
        return new Headquarters(null, city, country);
    }

    /** A new city was entered: it wins over a selected location. */
    public boolean isNew() {
        return newCity != null && !newCity.isBlank();
    }

    public boolean isEmpty() {
        return locationId == null && !isNew();
    }
}
