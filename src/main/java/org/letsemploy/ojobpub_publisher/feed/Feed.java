package org.letsemploy.ojobpub_publisher.feed;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.common.Base;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.job.Job;

/** A named, publishable selection of one employer's jobs (spec 3.5). */
@Entity
@Table(name = "feeds")
@Getter
@Setter
@NoArgsConstructor
public class Feed extends Base {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "employer_id", nullable = false, updatable = false)
    private Employer employer;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 64)
    private String slug;

    private String description;

    @ManyToMany
    @JoinTable(name = "feed_jobs",
            joinColumns = @JoinColumn(name = "feed_id"),
            inverseJoinColumns = @JoinColumn(name = "job_id"))
    private Set<Job> jobs = new LinkedHashSet<>();

    /** The second path segment of the public feed URL (spec 5.1). */
    public String getUrlSegment() {
        return slug + "_" + getId();
    }
}
