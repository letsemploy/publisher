package org.letsemploy.ojobpub_publisher.user;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPictureRepo extends JpaRepository<UserPicture, UUID> {

    /** Which of these users have a picture, and its version - without the bytes. */
    interface Version {
        UUID getUserId();

        Instant getUpdatedAt();
    }

    /** Where a user's picture came from - without the bytes. */
    interface Origin {
        UserPicture.Source getSource();

        String getSourceUrl();
    }

    @Query("SELECT p.userId AS userId, p.updatedAt AS updatedAt FROM UserPicture p WHERE p.userId IN :ids")
    List<Version> findVersions(@Param("ids") Collection<UUID> ids);

    @Query("SELECT p.source AS source, p.sourceUrl AS sourceUrl FROM UserPicture p WHERE p.userId = :id")
    Optional<Origin> findOrigin(@Param("id") UUID id);
}
