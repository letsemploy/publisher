package io.letsemploy.publisher.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "category")
public class Category extends SlugEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "domain_id", nullable = false)
    private PublishingDomain domain;

    public PublishingDomain getDomain() {
        return domain;
    }

    public void setDomain(PublishingDomain domain) {
        this.domain = domain;
    }
}
