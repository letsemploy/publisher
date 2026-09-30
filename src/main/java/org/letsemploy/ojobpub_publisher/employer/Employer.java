package org.letsemploy.ojobpub_publisher.employer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.common.Base;
import org.letsemploy.ojobpub_publisher.location.Location;

@Entity
@Table(name = "employers")
@Getter
@Setter
public class Employer extends Base {

    @Column(nullable = false)
    private String name;

    /** Decorative; identity lives in the UUID, so no uniqueness (spec 3.6). */
    @Column(nullable = false, length = 64)
    private String slug;

    private String url;

    private String industry;

    /** Required: the published document must carry the employer's location (spec 3.1). */
    /**
     * One of the employer's own locations (spec 3.1). Required - EmployerService
     * refuses to save without it - but nullable in the database: the location
     * belongs to this employer, so the employer has to exist before it can.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "location_id")
    private Location headquarters;

    /** The first path segment of every public feed URL (spec 5.1). */
    public String getUrlSegment() {
        return slug + "_" + getId();
    }

    @Override
    public String toString() {
        return name;
    }
}
