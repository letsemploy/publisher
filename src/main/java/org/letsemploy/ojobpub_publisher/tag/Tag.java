package org.letsemploy.ojobpub_publisher.tag;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.NaturalId;

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

    @NaturalId(mutable = true)
    @Column(nullable = false, unique = true, length = MAX_LENGTH)
    private String name;

    public Tag(String name) {
        this.name = name;
    }

    /** Tags are normalized on input: trimmed and lower-cased (spec 3.4). */
    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase();
    }
}
