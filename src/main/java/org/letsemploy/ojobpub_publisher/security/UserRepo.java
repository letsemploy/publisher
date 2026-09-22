package org.letsemploy.ojobpub_publisher.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepo extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByIssuerAndSubject(String issuer, String subject);

    /** Exact, case-insensitive: email is how a human addresses an invitation (spec 2.6). */
    Optional<UserEntity> findByEmailIgnoreCase(String email);

}
