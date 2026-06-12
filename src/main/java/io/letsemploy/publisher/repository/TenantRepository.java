package io.letsemploy.publisher.repository;

import io.letsemploy.publisher.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, String> {
}
