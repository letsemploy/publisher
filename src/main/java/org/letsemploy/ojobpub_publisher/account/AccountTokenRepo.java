package org.letsemploy.ojobpub_publisher.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTokenRepo extends JpaRepository<AccountToken, UUID> {

    Optional<AccountToken> findByTokenHash(String tokenHash);

    List<AccountToken> findByAccountIdAndPurposeAndUsedAtIsNull(UUID accountId, AccountToken.Purpose purpose);
}
