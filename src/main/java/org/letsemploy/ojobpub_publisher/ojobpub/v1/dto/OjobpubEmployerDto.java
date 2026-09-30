package org.letsemploy.ojobpub_publisher.ojobpub.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"name", "url", "industry", "location"})
public record OjobpubEmployerDto(String name, String url, String industry, OjobpubLocationDto location) {
}
