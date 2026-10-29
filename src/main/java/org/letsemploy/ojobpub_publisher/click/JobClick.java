package org.letsemploy.ojobpub_publisher.click;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * The clicks on one job's link, on one day, from one country (spec 3.14). A
 * counter, never a visit: no address and no user agent is kept (spec 10).
 */
@Entity
@Table(name = "job_clicks")
@Getter
@NoArgsConstructor
public class JobClick implements Persistable<JobClick.Key> {

    /** ISO 3166 "unknown or unspecified": a key column cannot hold NULL safely. */
    public static final String UNKNOWN_COUNTRY = "ZZ";

    @Embeddable
    public record Key(
            @Column(name = "job_id", nullable = false) UUID jobId,
            @Column(nullable = false) LocalDate day,
            @Column(nullable = false, length = 2) String country) {
    }

    @EmbeddedId
    private Key id;

    /** The job's employer, which never changes (spec 3.3), so the dashboard needs no join. */
    @Column(name = "employer_id", nullable = false, updatable = false)
    private UUID employerId;

    @Column(nullable = false)
    private long clicks;

    /**
     * Whether this row came from the database. The key is assigned, not
     * generated, so Spring Data would take every instance for an existing row and
     * merge it: a SELECT, then an INSERT - and a row a concurrent first click
     * committed in between would be overwritten with a count of one. A new
     * counter is inserted instead, and a concurrent one fails on the key, which
     * {@link JobClickService#record} answers by counting again.
     */
    @Transient
    @Getter(AccessLevel.NONE)
    private boolean stored;

    @PostLoad
    @PostPersist
    void markStored() {
        this.stored = true;
    }

    @Override
    public boolean isNew() {
        return !stored;
    }

    JobClick(Key id, UUID employerId) {
        this.id = id;
        this.employerId = employerId;
        this.clicks = 1;
    }
}
