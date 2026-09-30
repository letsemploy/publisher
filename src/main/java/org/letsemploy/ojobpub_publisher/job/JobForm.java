package org.letsemploy.ojobpub_publisher.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Binds the job form (spec 7.11), and is what the API builds too. Web-layer only;
 * never the published contract. Its shape is checked by {@code JobService}
 * (spec 9.6); the rules that need the employer's records are checked there as well.
 */
@Getter
@Setter
@JobFormShape
public class JobForm {

    private UUID id;
    @NotBlank(message = "{validation.title.required}")
    private String title;
    @Size(max = 1000, message = "{validation.description.tooLong}")
    private String description;
    @NotBlank(message = "{validation.url.required}")
    @Pattern(regexp = "(?s)https?://.*", message = "{validation.url.absolute}")
    private String url;
    @NotBlank(message = "{validation.language.required}")
    @Size(min = 2, max = 2, message = "{validation.language.required}")
    private String language;
    private String referenceId;
    private String category;
    @NotBlank(message = "{validation.jobType.required}")
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

    /** Trims what is stored trimmed, so the constraints see what would be saved. */
    public void trim() {
        title = trimmed(title);
        description = trimmed(description);
        url = trimmed(url);
        referenceId = trimmed(referenceId);
        category = trimmed(category);
        salaryCurrency = trimmed(salaryCurrency);
    }

    private static String trimmed(String s) {
        return s == null ? null : s.trim();
    }

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
