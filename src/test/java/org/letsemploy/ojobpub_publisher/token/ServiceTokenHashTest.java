package org.letsemploy.ojobpub_publisher.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * How a token's secret is stored (spec 2.8): SHA-256, since a random 32-byte
 * secret gains nothing from a slow hash and bcrypt cost every API call. A token
 * from before keeps working with its bcrypt hash, which is replaced on its first
 * use - the same secret, so nothing has to be redeployed.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class ServiceTokenHashTest {

    @Autowired private ServiceTokenService tokens;
    @Autowired private ServiceTokenRepo tokenRepo;
    @Autowired private EmployerRepo employerRepo;
    @Autowired private UserRepo userRepo;
    @Autowired private MembershipRepo membershipRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID acme;
    private Actor admin;

    @BeforeEach
    void setUp() {
        acme = employerRepo.findAll().stream()
                .filter(e -> e.getName().equals("Acme AG")).findFirst().orElseThrow().getId();
        UserEntity dev = userRepo.findUniqueByEmail("dev@localhost").orElseThrow();
        Map<UUID, MembershipRole> memberships = new java.util.HashMap<>();
        membershipRepo.findByUserId(dev.getId())
                .forEach(m -> memberships.put(m.getEmployer().getId(), m.getRole()));
        admin = Actor.user(dev.getId(), dev.getDisplayName(), dev.getEmail(), true, memberships);
    }

    private ServiceTokenService.CreatedToken mint() {
        return tokens.create(acme, "hash-" + UUID.randomUUID(), MembershipRole.EDITOR,
                Set.of(TokenScope.JOBS_READ), admin);
    }

    private String storedHash(UUID tokenId) {
        return tokenRepo.findById(tokenId).orElseThrow().getSecretHash();
    }

    /** The part after the dot: what is hashed. The prefix before it only finds the row. */
    private static String secretPart(String presented) {
        return presented.substring(presented.indexOf('.') + 1);
    }

    @Test
    void aNewTokenIsStoredAsSha256AndWorks() {
        var created = mint();

        assertThat(storedHash(created.token().getId()))
                .startsWith(ServiceTokenService.SHA256)
                .isEqualTo(ServiceTokenService.hash(secretPart(created.secret())))
                .doesNotContain(secretPart(created.secret()));
        assertThat(tokens.authenticate(created.secret())).isPresent();
    }

    @Test
    void aWrongSecretIsRefused() {
        var created = mint();
        String presented = created.secret();
        String wrong = presented.substring(0, presented.length() - 1)
                + (presented.endsWith("A") ? "B" : "A");

        assertThat(tokens.authenticate(wrong)).isEmpty();
    }

    /** A token from before SHA-256: it still works, and from then on costs no bcrypt. */
    @Test
    void aBcryptTokenWorksAndIsUpgradedOnItsFirstUse() {
        var created = mint();
        String secret = secretPart(created.secret());
        ServiceToken token = tokenRepo.findById(created.token().getId()).orElseThrow();
        token.setSecretHash(passwordEncoder.encode(secret));
        tokenRepo.saveAndFlush(token);
        assertThat(storedHash(token.getId())).startsWith("{bcrypt}");

        assertThat(tokens.authenticate(created.secret())).isPresent();

        assertThat(storedHash(token.getId())).isEqualTo(ServiceTokenService.hash(secret));
        assertThat(tokens.authenticate(created.secret())).as("the same secret, after the upgrade").isPresent();
    }

    /** A refusal writes nothing: neither last-used nor an upgraded hash. */
    @Test
    void aWrongSecretLeavesABcryptHashAsItWas() {
        var created = mint();
        ServiceToken token = tokenRepo.findById(created.token().getId()).orElseThrow();
        String bcrypt = passwordEncoder.encode(secretPart(created.secret()));
        token.setSecretHash(bcrypt);
        tokenRepo.saveAndFlush(token);

        String prefix = created.secret().substring(0, created.secret().indexOf('.'));
        assertThat(tokens.authenticate(prefix + ".not-the-secret")).isEmpty();

        assertThat(storedHash(token.getId())).isEqualTo(bcrypt);
    }
}
