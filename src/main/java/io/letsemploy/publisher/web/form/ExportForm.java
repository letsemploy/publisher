package io.letsemploy.publisher.web.form;

import java.util.ArrayList;
import java.util.List;

public class ExportForm {
    private String domainId;
    private String employerId;
    private String name;
    private String description;
    private boolean active;
    private List<String> jobIds = new ArrayList<>();

    public static ExportForm from(io.letsemploy.publisher.domain.JobExport export) {
        ExportForm form = new ExportForm();
        form.setDomainId(export.getDomain().getId());
        form.setEmployerId(export.getEmployer() == null ? null : export.getEmployer().getId());
        form.setName(export.getName());
        form.setDescription(export.getDescription());
        form.setActive(export.isActive());
        form.setJobIds(export.getJobs().stream().map(io.letsemploy.publisher.domain.Job::getId).toList());
        return form;
    }

    public String domainId() { return domainId; }
    public String employerId() { return employerId; }
    public String name() { return name; }
    public String description() { return description; }
    public boolean active() { return active; }
    public List<String> jobIds() { return jobIds == null ? List.of() : jobIds; }

    public String getDomainId() { return domainId; }
    public void setDomainId(String domainId) { this.domainId = domainId; }
    public String getEmployerId() { return employerId; }
    public void setEmployerId(String employerId) { this.employerId = employerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<String> getJobIds() { return jobIds; }
    public void setJobIds(List<String> jobIds) { this.jobIds = jobIds; }
}
