package org.letsemploy.ojobpub_publisher.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepo extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByIssuerAndSubject(String issuer, String subject);

    /** Exact, case-insensitive: email is how a human addresses an invitation (spec 2.6). */
    List<UserEntity> findAllByEmailIgnoreCase(String email);

    /**
     * The one account holding this address, if exactly one does (spec 2.2, 2.6).
     *
     * <p>Email is not an identity and is not unique here: two issuers may vouch for
     * the same address, and a provider may reassign one. An address held by more
     * than one account names nobody in particular, so it is treated as naming no
     * one - which for an invitation means answering exactly as for an unknown
     * address, rather than guessing or failing.
     */
    default Optional<UserEntity> findUniqueByEmail(String email) {
        List<UserEntity> found = findAllByEmailIgnoreCase(email);
        return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
    }

}
