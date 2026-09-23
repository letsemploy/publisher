package org.letsemploy.ojobpub_publisher.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expiry and renewal against the real service (spec 2.8).
 *
 * <p>The promise being tested is narrow and load-bearing: a lapsed token refuses
 * requests, renewing it brings it back, and <em>the same secret still works</em>.
 * If that last one were false the feature would be a scheduled outage, which is
 * exactly what the specification used to refuse expiry over.
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@Transactional
class ServiceTokenExpiryTest {

    @Autowired private ServiceTokenService tokens;
    @Autowired private ServiceTokenRepo tokenRepo;
    @Autowired private EmployerRepo employerRepo;
    @Autowired private UserRepo userRepo;
    @Autowired private MembershipRepo membershipRepo;

    private UUID acme;
    private Actor admin;

    @BeforeEach
    void setUp() {
        acme = employerRepo.findAll().stream()
                .filter(e -> e.getName().equals("Acme AG")).findFirst().orElseThrow().getId();
        admin = asUser(userRepo.findByEmailIgnoreCase("dev@localhost").orElseThrow());
    }

    private Actor asUser(UserEntity user) {
        Map<UUID, MembershipRole> memberships = new java.util.HashMap<>();
        membershipRepo.findByUserId(user.getId())
                .forEach(m -> memberships.put(m.getEmployer().getId(), m.getRole()));
        return Actor.user(user.getId(), user.getDisplayName(), user.getEmail(),
                user.getRole() == UserEntity.Role.ADMIN, memberships);
    }

    private ServiceTokenService.CreatedToken mint(String name) {
        return tokens.create(acme, name, MembershipRole.EDITOR,
                Set.of(TokenScope.JOBS_READ), admin);
    }

    private void expire(UUID tokenId) {
        ServiceToken token = tokenRepo.findById(tokenId).orElseThrow();
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        tokenRepo.save(token);
    }

    @Test
    void aNewTokenIsGivenAnExpiry() {
        assertThat(mint("fresh").getToken().getExpiresAt())
                .isNotNull()
                .isAfter(Instant.now().plus(300, ChronoUnit.DAYS));
    }

    @Test
    void anExpiredTokenDoesNotAuthenticate() {
        var created = mint("lapsing");
        assertThat(tokens.authenticate(created.getSecret())).isPresent();
        expire(created.getToken().getId());
        assertThat(tokens.authenticate(created.getSecret())).isEmpty();
    }

    /**
     * last-used answers "when did this last work" — it is the column the register
     * leans on to surface a forgotten integration (spec 7.17). Stamping it on a
     * refused call would make a dead token look alive, and would turn an
     * unauthenticated path into a write.
     */
    @Test
    void aRefusedCallIsNotRecordedAsUse() {
        var created = mint("refused");
        expire(created.getToken().getId());
        tokens.authenticate(created.getSecret());
        assertThat(tokenRepo.findById(created.getToken().getId()).orElseThrow().getLastUsedAt())
                .isNull();
    }

    /** The promise: nothing to redeploy. */
    @Test
    void renewingBringsItBackWithTheSameSecret() {
        var created = mint("renewable");
        String secretHashBefore = created.getToken().getSecretHash();
        expire(created.getToken().getId());
        assertThat(tokens.authenticate(created.getSecret())).isEmpty();

        tokens.renew(created.getToken().getId(), admin);

        assertThat(tokens.authenticate(created.getSecret()))
                .as("the original secret still authenticates")
                .isPresent();
        assertThat(tokenRepo.findById(created.getToken().getId()).orElseThrow().getSecretHash())
                .as("renewal does not re-mint anything")
                .isEqualTo(secretHashBefore);
    }

    /**
     * Extended from now, not from the date it lapsed on — otherwise a token dead
     * for eight months would come back with four months left.
     */
    @Test
    void renewalExtendsFromNowNotFromTheOldDate() {
        var created = mint("stale");
        ServiceToken token = tokenRepo.findById(created.getToken().getId()).orElseThrow();
        token.setExpiresAt(Instant.now().minus(240, ChronoUnit.DAYS));
        tokenRepo.save(token);

        tokens.renew(token.getId(), admin);

        assertThat(tokenRepo.findById(token.getId()).orElseThrow().getExpiresAt())
                .isAfter(Instant.now().plus(300, ChronoUnit.DAYS));
    }

    @Test
    void aRevokedTokenCannotBeRenewed() {
        var created = mint("dead");
        tokens.revoke(created.getToken().getId(), admin);
        assertThatThrownBy(() -> tokens.renew(created.getToken().getId(), admin))
                .isInstanceOf(ValidationFailure.class)
                .hasMessageContaining("token");
        assertThat(tokens.authenticate(created.getSecret())).isEmpty();
    }

    /**
     * The guard that keeps expiry from being decorative: a credential able to
     * extend its own life never expires. {@code create} refuses the same act.
     */
    @Test
    void aTokenCannotRenewItself() {
        var created = mint("self");
        Actor itself = Actor.serviceToken(created.getToken().getId(), "self",
                acme, MembershipRole.OWNER, Set.of(TokenScope.JOBS_READ));
        assertThatThrownBy(() -> tokens.renew(created.getToken().getId(), itself))
                .isInstanceOf(NotFoundException.class);
    }

    /** Renewal is an owner's act, and a non-owner gets 404, never 403. */
    @Test
    void anEditorCannotRenew() {
        var created = mint("guarded");
        Actor editor = asUser(userRepo.findByEmailIgnoreCase("member@example.com").orElseThrow());
        assertThatThrownBy(() -> tokens.renew(created.getToken().getId(), editor))
                .isInstanceOf(NotFoundException.class);
    }

    /** Renewal is allowed at any time; an owner need not wait for the warning. */
    @Test
    void aHealthyTokenMayBeRenewedEarly() {
        var created = mint("early");
        Instant before = created.getToken().getExpiresAt();
        tokens.renew(created.getToken().getId(), admin);
        assertThat(tokenRepo.findById(created.getToken().getId()).orElseThrow().getExpiresAt())
                .isAfterOrEqualTo(before);
    }
}
