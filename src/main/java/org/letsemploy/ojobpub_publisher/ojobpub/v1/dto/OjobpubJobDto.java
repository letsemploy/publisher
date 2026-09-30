package org.letsemploy.ojobpub_publisher.ojobpub.v1.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"title", "description", "category", "referenceId", "jobType", "workType",
        "experienceLevel", "workLoad", "salary", "locations", "publishedAt", "startDate",
        "endDate", "applyBefore", "language", "url", "tags"})
public record OjobpubJobDto(
        String title,
        String description,
        String category,
        String referenceId,
        String jobType,
        String workType,
        String experienceLevel,
        OjobpubWorkloadDto workLoad,
        OjobpubSalaryDto salary,
        List<OjobpubLocationDto> locations,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate publishedAt,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate applyBefore,
        String language,
        String url,
        List<String> tags) {
}
