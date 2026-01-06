package org.letsemploy.ojobpub_publisher.job;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/** Binds the job form (spec 7.11). Web-layer only; never the published contract. */
@Data
public class JobForm {

    private UUID id;
    private String title;
    private String description;
    private String url;
    private String language;
    private String referenceId;
    private String category;
    private String jobType;
    private String workType;
    private String experienceLevel;
    private Integer workLoadPercentMin;
    private Integer workLoadPercentMax;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String salaryCurrency;
    private String salaryInterval;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate applyBefore;

    /** Submitted by the chip pickers as bare ids (spec 7.8). */
    private List<Long> tags = new ArrayList<>();
    private List<UUID> locations = new ArrayList<>();

    /** Currency and interval become required as soon as an amount is present (spec 3.3). */
    public boolean isSalaryPresent() {
        return salaryMin != null || salaryMax != null;
    }

    public static JobForm of(Job job) {
        JobForm f = new JobForm();
        f.id = job.getId();
        f.title = job.getTitle();
        f.description = job.getDescription();
        f.url = job.getUrl();
        f.language = job.getLanguageCode();
        f.referenceId = job.getReferenceId();
        f.category = job.getCategory();
        f.jobType = job.getJobType() == null ? null : job.getJobType().name().toLowerCase();
        f.workType = job.getWorkType() == null ? null : job.getWorkType().name().toLowerCase();
        f.experienceLevel = job.getExperienceLevel() == null
                ? null : job.getExperienceLevel().name().toLowerCase();
        f.workLoadPercentMin = job.getWorkLoadPercentMin();
        f.workLoadPercentMax = job.getWorkLoadPercentMax();
        f.salaryMin = job.getSalaryMin();
        f.salaryMax = job.getSalaryMax();
        f.salaryCurrency = job.getSalaryCurrency();
        f.salaryInterval = job.getSalaryInterval() == null
                ? null : job.getSalaryInterval().name().toLowerCase();
        f.startDate = job.getStartDate();
        f.endDate = job.getEndDate();
        f.applyBefore = job.getApplyBefore();
        f.tags = job.getTags().stream().map(t -> t.getId()).toList();
        f.locations = job.getLocations().stream().map(l -> l.getId()).toList();
        return f;
    }
}
