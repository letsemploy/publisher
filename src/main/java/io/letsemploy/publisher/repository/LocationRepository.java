package io.letsemploy.publisher.repository;

import io.letsemploy.publisher.domain.Location;
import io.letsemploy.publisher.domain.PublishingDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, String> {
    List<Location> findAllByDomainOrderByCityAsc(PublishingDomain domain);
}
