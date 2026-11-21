package org.letsemploy.ojobpub_publisher.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditRepo;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rules of a permalink (spec 3.13, 5.5): only its employer's members work on
 * it, it only ever points at its employer's own feeds, and the log records what
 * changed - once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class PermalinkServiceTest {

    /** Fixed ids from the seed. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID FEED_ALL = UUID.fromString("cd58289d-022e-4a1e-df83-7e5f60718293");
    private static final UUID FEED_ENGINEERING = UUID.fromString("de69390e-133f-4b2f-e094-8f6071829304");
    private static final UUID CAREERS = UUID.fromString("5a5a5a5a-5a5a-4a5a-8a5a-5a5a5a5a5a5a");

    @Autowired
    private PermalinkService permalinkService;
    @Autowired
    private FeedService feedService;
    @Autowired
    private FeedRepo feedRepo;
    @Autowired
    private EmployerRepo employerRepo;
    @Autowired
    private AuditRepo auditRepo;
    @Autowired
    private PermalinkRepo permalinkRepo;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MockMvc mvc;
    /** No website is asked from a test. */
    @MockitoBean
    private PermalinkCheck website;

    private Employer acme;
    private final Actor editor = Actor.user(UUID.randomUUID(), "Editor", "editor@example.com", false,
            Map.of(ACME, MembershipRole.EDITOR));
    private final Actor stranger = Actor.user(UUID.randomUUID(), "Stranger", "stranger@example.com", false,
            Map.of());

    @BeforeEach
    void setUp() {
        acme = employerRepo.findById(ACME).orElseThrow();
    }

    private List<AuditEvent> events(AuditAction action, UUID permalink) {
        return auditRepo.findAll().stream()
                .filter(e -> e.getAction() == action && permalink.toString().equals(e.getTargetId()))
                .toList();
    }

    /** Another employer's feed, saved directly. */
    private Feed anotherEmployersFeed() {
        Employer other = new Employer();
        other.setName("Other Co");
        other.setSlug("other-co");
        other = employerRepo.save(other);
        Feed feed = new Feed();
        feed.setEmployer(other);
        feed.setName("Theirs");
        feed.setSlug("theirs");
        return feedRepo.save(feed);
    }

    @Test
    void anEditorCreatesOneAndItIsRecorded() {
        Permalink created = permalinkService.save(null, acme, "  Intranet  ", "  For staff ", null, FEED_ALL, editor);

        assertThat(created.getName()).isEqualTo("Intranet");
        assertThat(created.getDescription()).isEqualTo("For staff");
        assertThat(created.getFeed().getId()).isEqualTo(FEED_ALL);
        assertThat(events(AuditAction.PERMALINK_CREATED, created.getId())).singleElement()
                .satisfies(e -> assertThat(e.getDetail()).isEqualTo("All jobs"));
    }

    /** Refused exactly like a feed that does not exist: nothing is disclosed (spec 3.13). */
    @Test
    void anotherEmployersFeedIsRefusedLikeAnUnknownOne() {
        Feed theirs = anotherEmployersFeed();

        ValidationFailure foreign = org.junit.jupiter.api.Assertions.assertThrows(ValidationFailure.class,
                () -> permalinkService.retarget(CAREERS, theirs.getId(), editor));
        ValidationFailure unknown = org.junit.jupiter.api.Assertions.assertThrows(ValidationFailure.class,
                () -> permalinkService.retarget(CAREERS, UUID.randomUUID(), editor));
        assertThat(foreign.getFieldErrors()).isEqualTo(unknown.getFieldErrors()).containsKey("feed");
        assertThatThrownBy(() -> permalinkService.save(null, acme, "Sneaky", null, null, theirs.getId(), editor))
                .isInstanceOf(ValidationFailure.class);
        assertThat(permalinkService.findVisible(CAREERS, editor).getFeed().getId()).isEqualTo(FEED_ENGINEERING);
    }

    /** A non-member is told it does not exist (spec 2.4). */
    @Test
    void aStrangerCannotSeeOrChangeOne() {
        assertThatThrownBy(() -> permalinkService.findVisible(CAREERS, stranger))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> permalinkService.retarget(CAREERS, null, stranger))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> permalinkService.delete(CAREERS, stranger))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void namesAreUniqueWithinAnEmployer() {
        assertThatThrownBy(() -> permalinkService.save(null, acme, "careers PAGE", null, null, null, editor))
                .isInstanceOf(ValidationFailure.class)
                .satisfies(e -> assertThat(((ValidationFailure) e).getFieldErrors()).containsKey("name"));
        // Keeping one's own name on an edit is not a duplicate.
        permalinkService.save(CAREERS, acme, "Careers page", "new text", "example.com", FEED_ENGINEERING, editor);
    }

    @Test
    void switchingIsRecordedWithBothFeedsAndOnlyWhenItChangesSomething() {
        permalinkService.retarget(CAREERS, FEED_ENGINEERING, editor);
        assertThat(events(AuditAction.PERMALINK_RETARGETED, CAREERS)).isEmpty();

        permalinkService.retarget(CAREERS, null, editor);
        assertThat(events(AuditAction.PERMALINK_RETARGETED, CAREERS)).singleElement()
                .satisfies(e -> assertThat(e.getDetail()).isEqualTo("Engineering → —"));
    }

    /** Deleting the feed leaves the permalink, publishing nothing, and says so in the log. */
    @Test
    void deletingTheFeedClearsTheTargetAndRecordsIt() {
        feedService.delete(FEED_ENGINEERING, editor);

        assertThat(permalinkService.findVisible(CAREERS, editor).getFeed()).isNull();
        assertThat(events(AuditAction.PERMALINK_RETARGETED, CAREERS)).singleElement()
                .satisfies(e -> assertThat(e.getDetail()).isEqualTo("Engineering → —"));
    }

    @Test
    void deletingIsRecorded() {
        permalinkService.delete(CAREERS, editor);

        assertThat(permalinkService.findForPublishing(CAREERS)).isEmpty();
        assertThat(events(AuditAction.PERMALINK_DELETED, CAREERS)).hasSize(1);
    }

    /** A whole URL is cut down to its host; anything that is no host name is refused (spec 7.23). */
    @Test
    void theWebsiteIsAHost() {
        assertThat(PermalinkService.host(" https://WWW.Acme.example/careers?x#y ")).isEqualTo("www.acme.example");
        assertThat(PermalinkService.host("bücher.example.")).isEqualTo("xn--bcher-kva.example");
        assertThat(PermalinkService.host("  ")).isNull();
        // The apex domain only, by the Public Suffix List: no www., no other subdomain, no public suffix.
        for (String bad : new String[] {"localhost", "example.com:8443", "10.0.0.1", "me@example.com", "example..com",
                "www.example.com", "jobs.example.com", "www.acme.co.uk", "co.uk", "acme.example"}) {
            assertThatThrownBy(() -> permalinkService.save(null, acme, "Intranet", null, bad, null, editor))
                    .as(bad).isInstanceOf(ValidationFailure.class)
                    .satisfies(e -> assertThat(((ValidationFailure) e).getFieldErrors()).containsKey("websiteHost"));
        }
        Permalink saved = permalinkService.save(null, acme, "Intranet", null, "https://Acme.co.UK/jobs", null, editor);
        assertThat(saved.getWebsiteHost()).isEqualTo("acme.co.uk");
    }

    /**
     * The answer is kept without moving the permalink's date, which dates the
     * served document; a new host forgets what was found on the old one.
     */
    @Test
    void aCheckIsKeptUntilTheHostChanges() {
        Instant dated = permalinkRepo.findById(CAREERS).orElseThrow().getLastModifiedAt();
        when(website.check(eq("example.com"), eq(CAREERS))).thenReturn(PermalinkCheck.Result.NOT_FOUND);

        assertThat(permalinkService.check(CAREERS, editor)).isEqualTo(PermalinkCheck.Result.NOT_FOUND);
        // The answer is written by a bulk UPDATE, past the entity this transaction holds.
        entityManager.flush();
        entityManager.clear();
        Permalink checked = permalinkRepo.findById(CAREERS).orElseThrow();
        assertThat(checked.getCheckResult()).isEqualTo(PermalinkCheck.Result.NOT_FOUND);
        assertThat(checked.getCheckedAt()).isNotNull();
        assertThat(checked.getLastModifiedAt()).isEqualTo(dated);

        // Once a moment per person: the check asks a host someone typed.
        assertThatThrownBy(() -> permalinkService.check(CAREERS, editor))
                .isInstanceOf(ValidationFailure.class)
                .satisfies(e -> assertThat(((ValidationFailure) e).getFieldErrors()).containsKey("check"));
        assertThatThrownBy(() -> permalinkService.check(CAREERS, stranger)).isInstanceOf(NotFoundException.class);

        Permalink moved = permalinkService.save(CAREERS, employerRepo.findById(ACME).orElseThrow(), checked.getName(), checked.getDescription(),
                "example.org", FEED_ENGINEERING, editor);
        assertThat(moved.getCheckResult()).isNull();
        assertThat(moved.getCheckedAt()).isNull();
    }

    @Test
    void withoutAWebsiteThereIsNothingToCheck() {
        UUID partner = UUID.fromString("5b5b5b5b-5b5b-4b5b-8b5b-5b5b5b5b5b5b");
        assertThatThrownBy(() -> permalinkService.check(partner, editor))
                .isInstanceOf(ValidationFailure.class)
                .satisfies(e -> assertThat(((ValidationFailure) e).getFieldErrors()).containsKey("websiteHost"));
    }

    /** From the edit page: the check runs and the page shows it; without a website it says why. */
    @Test
    void theEditPageChecksAndComesBack() throws Exception {
        when(website.check(eq("example.com"), eq(CAREERS))).thenReturn(PermalinkCheck.Result.REDIRECT);
        mvc.perform(post("/feeds/permalinks/" + CAREERS + "/check"))
                .andExpect(redirectedUrl("/feeds/permalinks/" + CAREERS + "/update#website"));
        entityManager.clear();
        assertThat(permalinkRepo.findById(CAREERS).orElseThrow().getCheckResult())
                .isEqualTo(PermalinkCheck.Result.REDIRECT);

        UUID partner = UUID.fromString("5b5b5b5b-5b5b-4b5b-8b5b-5b5b5b5b5b5b");
        mvc.perform(post("/feeds/permalinks/" + partner + "/check"))
                .andExpect(flash().attribute("errorMsg", "permalink.check.needsHost"));
    }
}
