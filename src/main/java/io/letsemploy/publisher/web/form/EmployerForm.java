package io.letsemploy.publisher.web.form;

public class EmployerForm {
    private String domainId;
    private String headquartersId;
    private String name;
    private String slug;
    private String description;
    private String industry;
    private String url;

    public String getDomainId() { return domainId; }
    public void setDomainId(String domainId) { this.domainId = domainId; }
    public String getHeadquartersId() { return headquartersId; }
    public void setHeadquartersId(String headquartersId) { this.headquartersId = headquartersId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
