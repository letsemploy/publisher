package org.letsemploy.ojobpub_publisher.ojobpub.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"min", "max", "currency", "interval"})
public record OjobpubSalaryDto(
        /* BigDecimal, not float: narrowing loses accuracy on six-figure salaries (spec 6.7). */
        BigDecimal min,
        BigDecimal max,
        String currency,
        String interval) {
}
