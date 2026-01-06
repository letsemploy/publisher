package org.letsemploy.ojobpub_publisher.common;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
public abstract class Base {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, unique = true)
    private UUID id;

    @Column(updatable = false)
    @CreatedDate
    protected Instant createdAt;

    @LastModifiedDate
    protected Instant lastModifiedAt;

    @PrePersist
    public void onPrePersist() {
        setCreatedAt(Instant.now());
        setLastModifiedAt(Instant.now());
    }

    @PreUpdate
    public void onPreUpdate() {
        setLastModifiedAt(Instant.now());
    }
}
