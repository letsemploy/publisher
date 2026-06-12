package io.letsemploy.publisher.repository;

import io.letsemploy.publisher.domain.PublishingDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublishingDomainRepository extends JpaRepository<PublishingDomain, String> {
    Optional<PublishingDomain> findBySlug(String slug);
}
