package org.letsemploy.ojobpub_publisher.token;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.membership.Membership;
import org.letsemploy.ojobpub_publisher.membership.MembershipRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service tokens (spec 2.8). Only an owner of the employer may manage them. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceTokenService {

    private static final String PREFIX = "ojp_";
    private static final int PREFIX_RANDOM = 8;
    private static final int SECRET_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    private final ServiceTokenRepo tokenRepo;
    private final MembershipRepo membershipRepo;
    private final MembershipService membershipService;
    private final EmployerRepo employerRepo;
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    /** A created token and its secret, which is returned exactly once (spec 2.8). */
    @Value
    public static class CreatedToken {
        ServiceToken token;
        String secret;
    }

    public List<ServiceToken> forEmployer(UUID employerId, Actor actor) {
        membershipService.requireOwner(actor, employerId);
        return tokenRepo.findByEmployerIdOrderByCreatedAtDesc(employerId);
    }

    /**
     * Creates a token. Only an owner may (spec 2.8) - not an editor, whose
     * authority is over content, and the check is {@code requireOwner}, which a
     * platform admin also passes.
     */
    @Transactional
    public CreatedToken create(UUID employerId, String name, MembershipRole role,
                               Set<TokenScope> scopes, Actor actor) {
        membershipService.requireOwner(actor, employerId);
        if (actor.isToken()) {
            // A credential must not mint another credential.
            throw new NotFoundException("Not found.");
        }
        if (name == null || name.isBlank()) {
            throw new ValidationFailure("name", "A name is required.");
        }
        if (scopes == null || scopes.isEmpty()) {
            throw new ValidationFailure("scopes", "Choose at least one scope.");
        }
        Employer employer = employerRepo.findById(employerId)
                .orElseThrow(() -> new NotFoundException("Employer not found: " + employerId));
        UserEntity creator = userRepo.findById(actor.getId())
                .orElseThrow(() -> new NotFoundException("User not found: " + actor.getId()));

        String prefix = PREFIX + randomText(PREFIX_RANDOM);
        String secretPart = randomText(SECRET_BYTES);

        ServiceToken token = new ServiceToken();
        token.setEmployer(employer);
        token.setName(name.trim());
        token.setPrefix(prefix);
        token.setSecretHash(passwordEncoder.encode(secretPart));
        token.setScopes(new java.util.LinkedHashSet<>(scopes));
        token.setCreatedBy(creator);
        ServiceToken saved = tokenRepo.save(token);

        // The token holds a membership role of its own (spec 2.8), but never
        // satisfies the last-owner rule (spec 2.7).
        membershipRepo.save(new Membership(saved, employer,
                role == null ? MembershipRole.EDITOR : role));

        log.info("User {} created service token {} for employer {}",
                actor.getId(), prefix, employerId);
        // Presented as prefix.secret so authentication can look up by prefix alone.
        return new CreatedToken(saved, prefix + "." + secretPart);
    }

    /** Revoking is not deleting: the row stays so the audit trail still resolves. */
    @Transactional
    public void revoke(UUID tokenId, Actor actor) {
        ServiceToken token = tokenRepo.findById(tokenId)
                .orElseThrow(() -> new NotFoundException("Token not found: " + tokenId));
        membershipService.requireOwner(actor, token.getEmployer().getId());
        if (token.isRevoked()) {
            return;
        }
        token.setRevokedAt(Instant.now());
        tokenRepo.save(token);
        log.info("User {} revoked service token {}", actor.getId(), token.getPrefix());
    }

    /** URL-safe random text; the source of both the prefix and the secret. */
    private String randomText(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    /**
     * Resolves a presented secret to an actor, or empty. Looks up by the indexed
     * prefix and then compares hashes, so it never scans (spec 10).
     */
    @Transactional
    public Optional<Actor> authenticate(String presented) {
        if (presented == null || !presented.startsWith(PREFIX) || !presented.contains(".")) {
            return Optional.empty();
        }
        String prefix = presented.substring(0, presented.indexOf('.'));
        String secret = presented.substring(presented.indexOf('.') + 1);

        Optional<ServiceToken> found = tokenRepo.findByPrefix(prefix);
        if (found.isEmpty() || found.get().isRevoked()
                || !passwordEncoder.matches(secret, found.get().getSecretHash())) {
            return Optional.empty();
        }
        ServiceToken token = found.get();
        token.setLastUsedAt(Instant.now());
        tokenRepo.save(token);

        MembershipRole role = membershipRepo
                .findByServiceTokenIdAndEmployerId(token.getId(), token.getEmployer().getId())
                .map(Membership::getRole)
                .orElse(MembershipRole.EDITOR);
        return Optional.of(Actor.serviceToken(token.getId(), token.getLabel(),
                token.getEmployer().getId(), role, token.getScopes()));
    }
}
