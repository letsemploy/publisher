package org.letsemploy.ojobpub_publisher.job;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.common.Base;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.location.Location;
import org.letsemploy.ojobpub_publisher.tag.Tag;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
public class Job extends Base {

    /** Set at creation and immutable thereafter (spec 3.3). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employer_id", nullable = false, updatable = false)
    private Employer employer;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private String url;

    @Column(name = "language_code", nullable = false, length = 2)
    private String languageCode;

    private String referenceId;

    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    private WorkType workType;

    @Enumerated(EnumType.STRING)
    private ExperienceLevel experienceLevel;

    private Integer workLoadPercentMin;

    private Integer workLoadPercentMax;

    @Column(precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(length = 3)
    private String salaryCurrency;

    @Enumerated(EnumType.STRING)
    private SalaryInterval salaryInterval;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDate applyBefore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.DRAFT;

    /** Stamped once on first activation and never moved afterwards (spec 4.2). */
    private LocalDate publishedAt;

    @ManyToMany
    @JoinTable(name = "job_tags",
            joinColumns = @JoinColumn(name = "job_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @OrderBy("name ASC")
    private Set<Tag> tags = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "job_locations",
            joinColumns = @JoinColumn(name = "job_id"),
            inverseJoinColumns = @JoinColumn(name = "location_id"))
    @OrderBy("city ASC")
    private Set<Location> locations = new HashSet<>();

    public boolean hasSalaryAmount() {
        return salaryMin != null || salaryMax != null;
    }

    public boolean hasWorkLoad() {
        return workLoadPercentMin != null || workLoadPercentMax != null;
    }
}
