package io.letsemploy.publisher.repository;

import io.letsemploy.publisher.domain.PublishingDomain;
import io.letsemploy.publisher.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, String> {
    List<Tag> findAllByDomainOrderByNameAsc(PublishingDomain domain);
    Optional<Tag> findByDomainAndSlug(PublishingDomain domain, String slug);
}
