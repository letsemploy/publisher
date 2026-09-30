package org.letsemploy.ojobpub_publisher.click;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The clicks on one job's link, on one day, from one country (spec 3.14). A
 * counter, never a visit: no address and no user agent is kept (spec 10).
 */
@Entity
@Table(name = "job_clicks")
@Getter
@NoArgsConstructor
public class JobClick {

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

    JobClick(Key id, UUID employerId) {
        this.id = id;
        this.employerId = employerId;
        this.clicks = 1;
    }
}
