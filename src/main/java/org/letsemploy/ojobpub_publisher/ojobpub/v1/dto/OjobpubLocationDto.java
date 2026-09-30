package org.letsemploy.ojobpub_publisher.ojobpub.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"city", "country"})
public record OjobpubLocationDto(
        String city,
        /* ISO 3166-1 alpha-2, upper case everywhere in the document (spec 6.3). */
        String country) {
}
