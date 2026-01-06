package org.letsemploy.ojobpub_publisher.employer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.common.Base;
import org.letsemploy.ojobpub_publisher.location.Location;

@Entity
@Table(name = "employers")
@Getter
@Setter
@NoArgsConstructor
public class Employer extends Base {

    @Column(nullable = false)
    private String name;

    /** Decorative; identity lives in the UUID, so no uniqueness (spec 3.6). */
    @Column(nullable = false, length = 64)
    private String slug;

    private String url;

    private String industry;

    /** Required: the published document must carry the employer's location (spec 3.1). */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
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
