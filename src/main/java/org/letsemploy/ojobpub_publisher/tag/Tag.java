package org.letsemploy.ojobpub_publisher.tag;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.letsemploy.ojobpub_publisher.employer.Employer;

@Entity
@Table(name = "tags")
@Getter
@Setter
@NoArgsConstructor
public class Tag {

    /** The published schema caps a tag at 28 characters (spec 3.4). */
    public static final int MAX_LENGTH = 28;

    /** The published schema allows at most 16 tags per job (spec 3.3). */
    public static final int MAX_PER_JOB = 16;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The employer this tag belongs to, and only it (spec 3.4). Fixed at creation. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employer_id", nullable = false, updatable = false)
    private Employer employer;

    /** Unique within its employer, not across the installation (spec 3.4). */
    @Column(nullable = false, length = MAX_LENGTH)
    private String name;

    public Tag(String name) {
        this.name = name;
    }

    public Tag(Employer employer, String name) {
        this(name);
        this.employer = employer;
    }

    /** Tags are normalized on input: trimmed and lower-cased (spec 3.4). */
    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase();
    }
}
