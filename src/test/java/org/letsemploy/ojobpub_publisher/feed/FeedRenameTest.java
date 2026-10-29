package org.letsemploy.ojobpub_publisher.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Editing a feed (spec 3.5): a rename onto another feed's name is a field error
 * like on create, not the unique key failing as a 500, and only the feed's own
 * employer's members may edit it.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class FeedRenameTest {

    /** Fixed ids from the seed: Acme, and its feeds "all" and "Engineering". */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID FEED_ALL = UUID.fromString("cd58289d-022e-4a1e-df83-7e5f60718293");
    private static final UUID FEED_ENGINEERING = UUID.fromString("de69390e-133f-4b2f-e094-8f6071829304");

    @Autowired
    private FeedService feedService;
    @Autowired
    private FeedRepo feedRepo;
    @Autowired
    private EmployerRepo employerRepo;

    private Employer acme;
    private final Actor editor = Actor.user(UUID.randomUUID(), "Editor", "editor@example.com", false,
            Map.of(ACME, MembershipRole.EDITOR));

    @BeforeEach
    void setUp() {
        acme = employerRepo.findById(ACME).orElseThrow();
    }

    @Test
    void renamingOntoAnotherFeedsNameIsAFieldError() {
        String taken = feedRepo.findById(FEED_ALL).orElseThrow().getName();

        assertThatThrownBy(() -> feedService.save(FEED_ENGINEERING, acme, taken.toUpperCase(),
                null, null, editor))
                .isInstanceOf(ValidationFailure.class)
                .satisfies(e -> assertThat(((ValidationFailure) e).fields()).containsExactly("name"));
    }

    /** Its own name, in another case, is not a clash with itself. */
    @Test
    void keepingItsOwnNameIsNotAClash() {
        String own = feedRepo.findById(FEED_ENGINEERING).orElseThrow().getName();

        assertThat(feedService.save(FEED_ENGINEERING, acme, own.toLowerCase(), null, null, editor)
                .getName()).isEqualTo(own.toLowerCase());
    }

    @Test
    void aStrangerCannotEditTheFeed() {
        Actor stranger = Actor.user(UUID.randomUUID(), "Stranger", "stranger@example.com", false, Map.of());

        assertThatThrownBy(() -> feedService.save(FEED_ENGINEERING, acme, "Hijacked", null, null, stranger))
                .isInstanceOf(NotFoundException.class);
    }
}
