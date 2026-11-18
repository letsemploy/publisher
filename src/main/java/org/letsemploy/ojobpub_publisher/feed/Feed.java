package org.letsemploy.ojobpub_publisher.feed;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.common.Base;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.job.Job;

/** A named, publishable selection of one employer's jobs (spec 3.5). */
@Entity
@Table(name = "feeds")
@Getter
@Setter
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

    /**
     * Adds a job, moving the feed's date: the join table alone leaves the feed's
     * row as it was, so its date would not move although its document did (spec 5.4).
     *
     * @return whether the job was not in the feed before
     */
    public boolean include(Job job) {
        boolean added = jobs.add(job);
        if (added) {
            setLastModifiedAt(Instant.now());
        }
        return added;
    }

    /** Removes a job by id, moving the feed's date; whether it was in the feed. */
    public boolean exclude(UUID jobId) {
        boolean removed = jobs.removeIf(j -> j.getId().equals(jobId));
        if (removed) {
            setLastModifiedAt(Instant.now());
        }
        return removed;
    }

    /** The second path segment of the public feed URL (spec 5.1). */
    public String getUrlSegment() {
        return slug + "_" + getId();
    }
}
