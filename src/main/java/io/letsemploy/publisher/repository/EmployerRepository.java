package io.letsemploy.publisher.repository;

import io.letsemploy.publisher.domain.Employer;
import io.letsemploy.publisher.domain.PublishingDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployerRepository extends JpaRepository<Employer, String> {
    List<Employer> findAllByDomainOrderByNameAsc(PublishingDomain domain);
}
