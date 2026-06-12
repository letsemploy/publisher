package io.letsemploy.publisher.repository;

import io.letsemploy.publisher.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findByExternalSubject(String externalSubject);
}
