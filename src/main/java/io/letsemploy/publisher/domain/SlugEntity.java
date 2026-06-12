package io.letsemploy.publisher.domain;

import io.letsemploy.publisher.support.Slugifier;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@MappedSuperclass
public abstract class SlugEntity extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 255)
    private String slug;

    @Column(length = 1000)
    private String description;

    @PrePersist
    @PreUpdate
    void updateSlug() {
        if (slug == null || slug.isBlank()) {
            slug = Slugifier.slugify(name);
        } else {
            slug = Slugifier.slugify(slug);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
