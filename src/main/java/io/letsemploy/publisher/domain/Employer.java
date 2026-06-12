package io.letsemploy.publisher.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "employer")
public class Employer extends SlugEntity {

    @Column(length = 255)
    private String industry;

    @Column(length = 500)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "domain_id", nullable = false)
    private PublishingDomain domain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "headquarters_location_id")
    private Location headquarters;

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public PublishingDomain getDomain() {
        return domain;
    }

    public void setDomain(PublishingDomain domain) {
        this.domain = domain;
    }

    public Location getHeadquarters() {
        return headquarters;
    }

    public void setHeadquarters(Location headquarters) {
        this.headquarters = headquarters;
    }
}
