package org.letsemploy.ojobpub_publisher.location;

import jakarta.validation.constraints.NotBlank;
import org.letsemploy.ojobpub_publisher.common.validation.CountryCode;

/** A location as typed: a city and an ISO country code (spec 3.2, 9.6). */
record LocationInput(
        @NotBlank(message = "{validation.city.required}") String city,
        @CountryCode String country) {
}
