package org.letsemploy.ojobpub_publisher.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepo extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByIssuerAndSubject(String issuer, String subject);
}
