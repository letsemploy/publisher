package org.letsemploy.ojobpub_publisher.employer;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployerRepo extends JpaRepository<Employer, UUID> {

    Page<Employer> findByIdInOrderByNameAsc(List<UUID> ids, Pageable pageable);

    Page<Employer> findAllByOrderByNameAsc(Pageable pageable);

    List<Employer> findAllByOrderByNameAsc();

    Page<Employer> findByNameContainingIgnoreCaseOrderByNameAsc(String name, Pageable pageable);
}
