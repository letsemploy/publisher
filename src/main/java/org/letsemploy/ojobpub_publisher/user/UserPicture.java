package org.letsemploy.ojobpub_publisher.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A person's picture (spec 3.7, 7.27), stored rather than linked so the browser
 * never asks a third party for it (spec 7.2). Keyed by the user's id, and gone
 * with the user (spec 2.13).
 *
 * <p>A table of its own, not a column on {@code users}: that row is read on every
 * request. The bytes are a plain {@code byte[]}, not a {@code @Lob}, because the
 * SQLite driver implements no {@code Blob}.
 */
@Entity
@Table(name = "user_pictures")
@Getter
@Setter
@NoArgsConstructor
public class UserPicture {

    /** Where the picture came from: the person's own upload outranks the provider's. */
    public enum Source {
        UPLOAD, PROVIDER
    }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private byte[] content;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    /** The provider's address it was fetched from; null for an upload. */
    @Column(name = "source_url")
    private String sourceUrl;

    /** When it last changed: the version in its URL, so a cache never shows an old one. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    UserPicture(UUID userId) {
        this.userId = userId;
    }
}
