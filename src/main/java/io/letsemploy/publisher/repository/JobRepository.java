package io.letsemploy.publisher.repository;

import io.letsemploy.publisher.domain.Job;
import io.letsemploy.publisher.domain.PublishingDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface JobRepository extends JpaRepository<Job, String>, JpaSpecificationExecutor<Job> {
    List<Job> findAllByDomainOrderByPublishedAtDescTitleAsc(PublishingDomain domain);
}
