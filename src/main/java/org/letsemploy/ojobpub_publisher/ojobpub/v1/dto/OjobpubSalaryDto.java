package org.letsemploy.ojobpub_publisher.ojobpub.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"min", "max", "currency", "interval"})
public class OjobpubSalaryDto {
    /** BigDecimal, not float: narrowing loses accuracy on six-figure salaries (spec 6.7). */
    private BigDecimal min;
    private BigDecimal max;
    private String currency;
    private String interval;
}
