package io.letsemploy.publisher.repository;

import io.letsemploy.publisher.domain.JobExport;
import io.letsemploy.publisher.domain.PublishingDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobExportRepository extends JpaRepository<JobExport, String> {
    List<JobExport> findAllByDomainOrderByNameAsc(PublishingDomain domain);
    Optional<JobExport> findByDomainSlugAndActiveTrue(String domainSlug);
    List<JobExport> findAllByDomainAndIdNot(PublishingDomain domain, String id);
}
