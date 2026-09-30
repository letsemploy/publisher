package org.letsemploy.ojobpub_publisher.ojobpub.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"minPercentage", "maxPercentage"})
public record OjobpubWorkloadDto(Integer minPercentage, Integer maxPercentage) {
}
