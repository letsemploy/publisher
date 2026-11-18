package org.letsemploy.ojobpub_publisher.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.audit.AuditRepo;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * The last two admins deleting their accounts at the same moment must not leave
 * the installation without one (spec 2.13). Not {@code @Transactional}: the point
 * is two transactions. MariaDB only - SQLite has one writer, so the race cannot
 * happen. The seeded admin is suspended meanwhile, so the two are the only ones.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
@DisabledIfEnvironmentVariable(named = "TEST_DB", matches = "(?i)sqlite")
class LastAdminRaceTest {

    @Autowired
    private AccountDeletionService deletion;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private AuditRepo auditRepo;
    @Autowired
    private PlatformTransactionManager transactions;

    private UserEntity first;
    private UserEntity second;

    @BeforeEach
    void twoAdmins() {
        setSuspended(true);
        first = admin("first");
        second = admin("second");
    }

    @AfterEach
    void restore() {
        userRepo.findById(first.getId()).ifPresent(userRepo::delete);
        userRepo.findById(second.getId()).ifPresent(userRepo::delete);
        // The log keeps no foreign keys, so the committed deletion's row stays: other
        // classes count ACCOUNT_DELETED rows.
        auditRepo.deleteAll(auditRepo.findAll().stream()
                .filter(e -> first.getId().equals(e.getSubjectUserId()) || second.getId().equals(e.getSubjectUserId()))
                .toList());
        setSuspended(false);
    }

    @Test
    void theLastTwoAdminsDeletingThemselvesAtOnceLeaveOne() throws Exception {
        TransactionStatus firstTx = transactions.getTransaction(new DefaultTransactionDefinition());
        deletion.delete(actor(first), first.getEmail());

        CompletableFuture<Throwable> other = CompletableFuture.supplyAsync(() -> {
            try {
                deletion.delete(actor(second), second.getEmail());
                return null;
            } catch (RuntimeException e) {
                return e;
            }
        });
        // Without the lock it finishes here, having counted two admins.
        Thread.sleep(500);
        transactions.commit(firstTx);

        assertThat(other.get(30, TimeUnit.SECONDS)).isInstanceOf(ValidationFailure.class);
        assertThat(userRepo.countByRoleAndSuspendedAtIsNull(UserEntity.Role.ADMIN)).isEqualTo(1);
    }

    private UserEntity admin(String name) {
        UserEntity user = new UserEntity();
        user.setIssuer("test");
        user.setSubject(name + "-" + UUID.randomUUID());
        user.setDisplayName(name);
        user.setEmail(name + "-" + UUID.randomUUID() + "@example.com");
        user.setRole(UserEntity.Role.ADMIN);
        return userRepo.save(user);
    }

    private static Actor actor(UserEntity user) {
        return Actor.user(user.getId(), user.getDisplayName(), user.getEmail(), true, Map.of());
    }

    private void setSuspended(boolean suspended) {
        UserEntity dev = userRepo.findUniqueByEmail("dev@localhost").orElseThrow();
        dev.setSuspendedAt(suspended ? Instant.now() : null);
        userRepo.save(dev);
    }
}
