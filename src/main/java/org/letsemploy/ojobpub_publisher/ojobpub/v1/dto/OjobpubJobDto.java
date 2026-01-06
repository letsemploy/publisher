package org.letsemploy.ojobpub_publisher.ojobpub.v1.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"title", "description", "category", "referenceId", "jobType", "workType",
        "experienceLevel", "workLoad", "salary", "locations", "publishedAt", "startDate",
        "endDate", "applyBefore", "language", "url", "tags"})
public class OjobpubJobDto {
    private String title;
    private String description;
    private String category;
    private String referenceId;
    private String jobType;
    private String workType;
    private String experienceLevel;
    private OjobpubWorkloadDto workLoad;
    private OjobpubSalaryDto salary;
    private List<OjobpubLocationDto> locations;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate publishedAt;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate applyBefore;

    private String language;
    private String url;
    private List<String> tags;
}
