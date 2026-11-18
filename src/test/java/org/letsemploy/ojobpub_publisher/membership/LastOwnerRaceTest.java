package org.letsemploy.ojobpub_publisher.membership;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.letsemploy.ojobpub_publisher.TestProfiles;
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
 * Two owners demoting each other at the same moment must not leave the employer
 * without an owner (spec 2.7). Not {@code @Transactional}: the point is two
 * transactions. MariaDB only - SQLite has one writer, so the race cannot happen.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
@DisabledIfEnvironmentVariable(named = "TEST_DB", matches = "(?i)sqlite")
class LastOwnerRaceTest {

    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");

    @Autowired
    private MembershipService membershipService;
    @Autowired
    private MembershipRepo membershipRepo;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private PlatformTransactionManager transactions;

    private UserEntity first;
    private UserEntity second;

    @BeforeEach
    void twoOwners() {
        first = userRepo.findUniqueByEmail("dev@localhost").orElseThrow();
        second = userRepo.findUniqueByEmail("member@example.com").orElseThrow();
        setRole(second, MembershipRole.OWNER);
    }

    @AfterEach
    void seedRoles() {
        setRole(first, MembershipRole.OWNER);
        setRole(second, MembershipRole.EDITOR);
    }

    @Test
    void ownersDemotingEachOtherAtOnceLeaveOneOwner() throws Exception {
        TransactionStatus firstTx = transactions.getTransaction(new DefaultTransactionDefinition());
        membershipService.changeRole(ACME, second.getId(), MembershipRole.EDITOR, ownerActor(first));

        // The other owner's demotion of the first, while the first's is not yet committed.
        CompletableFuture<Throwable> other = CompletableFuture.supplyAsync(() -> {
            try {
                membershipService.changeRole(ACME, first.getId(), MembershipRole.EDITOR, ownerActor(second));
                return null;
            } catch (RuntimeException e) {
                return e;
            }
        });
        // Without the lock it finishes here, having counted two owners.
        Thread.sleep(500);
        transactions.commit(firstTx);

        assertThat(other.get(30, TimeUnit.SECONDS)).isInstanceOf(ValidationFailure.class);
        assertThat(membershipRepo.countByEmployerIdAndRoleAndUserIsNotNullAndSuspendedAtIsNull(
                ACME, MembershipRole.OWNER)).isEqualTo(1);
    }

    private static Actor ownerActor(UserEntity user) {
        return Actor.user(user.getId(), user.getDisplayName(), user.getEmail(), false,
                Map.of(ACME, MembershipRole.OWNER));
    }

    private void setRole(UserEntity user, MembershipRole role) {
        Membership membership = membershipRepo.findByUserIdAndEmployerId(user.getId(), ACME).orElseThrow();
        membership.setRole(role);
        membershipRepo.save(membership);
    }
}
