package io.letsemploy.publisher.service;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExportPayload(
        String version,
        OffsetDateTime lastUpdated,
        EmployerPayload employer,
        List<JobPayload> jobs
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmployerPayload(String name, LocationPayload location, String industry, String url) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LocationPayload(String city, String country) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record WorkLoadPayload(BigDecimal minPercentage, BigDecimal maxPercentage) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SalaryPayload(BigDecimal min, BigDecimal max, String currency, String interval) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JobPayload(
            String language,
            LocalDate publishedAt,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate applyBefore,
            String category,
            String referenceId,
            String title,
            String description,
            String jobType,
            String experienceLevel,
            WorkLoadPayload workLoad,
            String workType,
            SalaryPayload salary,
            List<String> tags,
            List<LocationPayload> locations,
            String url
    ) {}
}
