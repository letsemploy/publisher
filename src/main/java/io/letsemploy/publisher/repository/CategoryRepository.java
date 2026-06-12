package io.letsemploy.publisher.repository;

import io.letsemploy.publisher.domain.Category;
import io.letsemploy.publisher.domain.PublishingDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, String> {
    List<Category> findAllByDomainOrderByNameAsc(PublishingDomain domain);
    Optional<Category> findByDomainAndSlug(PublishingDomain domain, String slug);
}
