package org.letsemploy.ojobpub_publisher.token;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceTokenRepo extends JpaRepository<ServiceToken, UUID> {

    /** Authentication looks up by the indexed prefix; it never scans (spec 10). */
    Optional<ServiceToken> findByPrefix(String prefix);

    List<ServiceToken> findByEmployerIdOrderByCreatedAtDesc(UUID employerId);
}
