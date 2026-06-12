package io.letsemploy.publisher.service;

import io.letsemploy.publisher.domain.ExperienceLevel;
import io.letsemploy.publisher.domain.JobType;
import io.letsemploy.publisher.domain.WorkType;

public record JobFilters(
        String query,
        String domainId,
        Boolean active,
        JobType jobType,
        ExperienceLevel experienceLevel,
        WorkType workType,
        String categoryId,
        String employerId,
        String locationId,
        String tagId
) {
}
