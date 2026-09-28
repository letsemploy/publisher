package org.letsemploy.ojobpub_publisher.feed;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
@NoArgsConstructor
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
}
