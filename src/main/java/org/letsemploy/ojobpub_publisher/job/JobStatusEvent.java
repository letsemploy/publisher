package org.letsemploy.ojobpub_publisher.job;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * One status change. "Why did this posting disappear from the feed?" must be
 * answerable without database access (spec 10).
 */
@Entity
@Table(name = "job_status_events")
@Getter
@Setter
@NoArgsConstructor
public class JobStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private JobStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private JobStatus toStatus;

    @Column(nullable = false)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public JobStatusEvent(Job job, JobStatus from, JobStatus to, String actor) {
        this.job = job;
        this.fromStatus = from;
        this.toStatus = to;
        this.actor = actor;
        this.occurredAt = Instant.now();
    }
}
