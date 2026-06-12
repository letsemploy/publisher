package io.letsemploy.publisher.web.form;

import io.letsemploy.publisher.domain.ExperienceLevel;
import io.letsemploy.publisher.domain.JobType;
import io.letsemploy.publisher.domain.SalaryInterval;
import io.letsemploy.publisher.domain.WorkType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class JobForm {
    private String domainId;
    private String referenceId;
    private String title;
    private String description;
    private String language = "en";
    private LocalDate publishedAt = LocalDate.now();
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate applyBefore;
    private JobType jobType = JobType.PERMANENT;
    private ExperienceLevel experienceLevel;
    private WorkType workType;
    private boolean active = true;
    private BigDecimal minWorkLoad;
    private BigDecimal maxWorkLoad;
    private BigDecimal minSalary;
    private BigDecimal maxSalary;
    private String salaryCurrency;
    private SalaryInterval salaryInterval;
    private String url;
    private String categoryName;
    private String employerId;
    private List<String> locationIds = new ArrayList<>();
    private String tagNames;

    public static JobForm from(io.letsemploy.publisher.domain.Job job) {
        JobForm form = new JobForm();
        form.setDomainId(job.getDomain().getId());
        form.setReferenceId(job.getReferenceId());
        form.setTitle(job.getTitle());
        form.setDescription(job.getDescription());
        form.setLanguage(job.getLanguage());
        form.setPublishedAt(job.getPublishedAt());
        form.setStartDate(job.getStartDate());
        form.setEndDate(job.getEndDate());
        form.setApplyBefore(job.getApplyBefore());
        form.setJobType(job.getJobType());
        form.setExperienceLevel(job.getExperienceLevel());
        form.setWorkType(job.getWorkType());
        form.setActive(job.isActive());
        form.setMinWorkLoad(job.getMinWorkLoad());
        form.setMaxWorkLoad(job.getMaxWorkLoad());
        form.setMinSalary(job.getMinSalary());
        form.setMaxSalary(job.getMaxSalary());
        form.setSalaryCurrency(job.getSalaryCurrency());
        form.setSalaryInterval(job.getSalaryInterval());
        form.setUrl(job.getUrl());
        form.setCategoryName(job.getCategory() == null ? null : job.getCategory().getName());
        form.setEmployerId(job.getEmployer() == null ? null : job.getEmployer().getId());
        form.setLocationIds(job.getLocations().stream().map(io.letsemploy.publisher.domain.Location::getId).toList());
        form.setTagNames(String.join(", ", job.getTags().stream().map(io.letsemploy.publisher.domain.Tag::getName).toList()));
        return form;
    }

    public String domainId() { return domainId; }
    public String referenceId() { return referenceId; }
    public String title() { return title; }
    public String description() { return description; }
    public String language() { return language; }
    public LocalDate publishedAt() { return publishedAt; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public LocalDate applyBefore() { return applyBefore; }
    public JobType jobType() { return jobType; }
    public ExperienceLevel experienceLevel() { return experienceLevel; }
    public WorkType workType() { return workType; }
    public boolean active() { return active; }
    public BigDecimal minWorkLoad() { return minWorkLoad; }
    public BigDecimal maxWorkLoad() { return maxWorkLoad; }
    public BigDecimal minSalary() { return minSalary; }
    public BigDecimal maxSalary() { return maxSalary; }
    public String salaryCurrency() { return salaryCurrency; }
    public SalaryInterval salaryInterval() { return salaryInterval; }
    public String url() { return url; }
    public String categoryName() { return categoryName; }
    public String employerId() { return employerId; }
    public List<String> locationIds() { return locationIds == null ? List.of() : locationIds; }
    public String tagNames() { return tagNames; }

    public String getDomainId() { return domainId; }
    public void setDomainId(String domainId) { this.domainId = domainId; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public LocalDate getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDate publishedAt) { this.publishedAt = publishedAt; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public LocalDate getApplyBefore() { return applyBefore; }
    public void setApplyBefore(LocalDate applyBefore) { this.applyBefore = applyBefore; }
    public JobType getJobType() { return jobType; }
    public void setJobType(JobType jobType) { this.jobType = jobType; }
    public ExperienceLevel getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(ExperienceLevel experienceLevel) { this.experienceLevel = experienceLevel; }
    public WorkType getWorkType() { return workType; }
    public void setWorkType(WorkType workType) { this.workType = workType; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public BigDecimal getMinWorkLoad() { return minWorkLoad; }
    public void setMinWorkLoad(BigDecimal minWorkLoad) { this.minWorkLoad = minWorkLoad; }
    public BigDecimal getMaxWorkLoad() { return maxWorkLoad; }
    public void setMaxWorkLoad(BigDecimal maxWorkLoad) { this.maxWorkLoad = maxWorkLoad; }
    public BigDecimal getMinSalary() { return minSalary; }
    public void setMinSalary(BigDecimal minSalary) { this.minSalary = minSalary; }
    public BigDecimal getMaxSalary() { return maxSalary; }
    public void setMaxSalary(BigDecimal maxSalary) { this.maxSalary = maxSalary; }
    public String getSalaryCurrency() { return salaryCurrency; }
    public void setSalaryCurrency(String salaryCurrency) { this.salaryCurrency = salaryCurrency; }
    public SalaryInterval getSalaryInterval() { return salaryInterval; }
    public void setSalaryInterval(SalaryInterval salaryInterval) { this.salaryInterval = salaryInterval; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getEmployerId() { return employerId; }
    public void setEmployerId(String employerId) { this.employerId = employerId; }
    public List<String> getLocationIds() { return locationIds; }
    public void setLocationIds(List<String> locationIds) { this.locationIds = locationIds; }
    public String getTagNames() { return tagNames; }
    public void setTagNames(String tagNames) { this.tagNames = tagNames; }
}
