package org.letsemploy.ojobpub_publisher.feed;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.common.Base;
import org.letsemploy.ojobpub_publisher.employer.Employer;

/**
 * A stable public URL that serves one of its employer's feeds, or none (spec
 * 3.13, 5.5). Switching the feed changes what the URL publishes, never the URL.
 *
 * <p>The feed is nullable: no target publishes the employer with no jobs. When
 * the targeted feed is deleted, the database clears the target.
 */
@Entity
@Table(name = "permalinks")
@Getter
@Setter
public class Permalink extends Base {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "employer_id", nullable = false, updatable = false)
    private Employer employer;

    @Column(nullable = false)
    private String name;

    private String description;

    /** Always one of the employer's own feeds - a rule of {@link PermalinkService}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_id")
    private Feed feed;

    /**
     * The employer's website that serves this permalink under its own domain, a
     * host only (spec 7.23); null until someone says which.
     */
    private String websiteHost;

    /** When the website was last checked; written by {@link PermalinkRepo#recordCheck}. */
    private Instant checkedAt;

    @Enumerated(EnumType.STRING)
    private PermalinkCheck.Result checkResult;
}
