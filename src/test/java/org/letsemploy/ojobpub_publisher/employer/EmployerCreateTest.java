package org.letsemploy.ojobpub_publisher.employer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * A new employer has its {@code all} feed from the moment it exists (spec 3.5),
 * made in the same transaction as the employer, by every caller of the service.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class EmployerCreateTest {

    @Autowired
    private EmployerService employerService;
    @Autowired
    private FeedService feedService;
    @Autowired
    private UserRepo userRepo;

    @Test
    void aNewEmployerHasItsAllFeed() {
        UserEntity creator = userRepo.findUniqueByEmail("dev@localhost").orElseThrow();
        Employer created = employerService.save(null, "Fresh AG", null, null, null,
                Headquarters.newLocation("Bern", "CH"),
                Actor.user(creator.getId(), creator.getDisplayName(), null, false, Map.of()));

        assertThat(feedService.findByEmployer(created.getId())).extracting(Feed::getSlug).containsExactly("all");
    }

    /** A member's employers sort as the database sorts an admin's: case does not count. */
    @Test
    void visibleEmployersSortIgnoringCase() {
        UserEntity creator = userRepo.findUniqueByEmail("dev@localhost").orElseThrow();
        Actor creating = Actor.user(creator.getId(), creator.getDisplayName(), null, false, Map.of());
        Employer bravo = employerService.save(null, "Bravo AG", null, null, null,
                Headquarters.newLocation("Bern", "CH"), creating);
        Employer alpha = employerService.save(null, "alpha GmbH", null, null, null,
                Headquarters.newLocation("Bern", "CH"), creating);
        Actor member = Actor.user(creator.getId(), creator.getDisplayName(), null, false,
                Map.of(bravo.getId(), MembershipRole.OWNER, alpha.getId(), MembershipRole.OWNER));

        assertThat(employerService.visibleTo(member)).extracting(Employer::getName)
                .containsExactly("alpha GmbH", "Bravo AG");
    }
}
