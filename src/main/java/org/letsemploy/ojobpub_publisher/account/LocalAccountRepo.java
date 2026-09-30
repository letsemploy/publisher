package org.letsemploy.ojobpub_publisher.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalAccountRepo extends JpaRepository<LocalAccount, UUID> {

    Optional<LocalAccount> findByEmailIgnoreCase(String email);
}
