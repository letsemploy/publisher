package org.letsemploy.ojobpub_publisher.feed;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditLog;
import org.letsemploy.ojobpub_publisher.common.ResourceLimits;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.common.validation.InputValidator;
import org.letsemploy.ojobpub_publisher.common.validation.NameInput;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.dto.OjobpubDto;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.service.OjobpubService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permalinks (spec 3.13, 5.5): a stable URL whose feed can be switched. Worked
 * on by the employer's members, like its feeds (spec 2.1), from the Feeds screen
 * and the management API alike.
 */
@Service
public class PermalinkService {

    private final PermalinkRepo permalinkRepo;
    private final FeedRepo feedRepo;
    private final ResourceLimits limits;
    private final OjobpubService ojobpubService;
    private final AuditLog auditLog;
    private final InputValidator inputs;

    public PermalinkService(PermalinkRepo permalinkRepo,
                            FeedRepo feedRepo,
                            ResourceLimits limits,
                            OjobpubService ojobpubService,
                            AuditLog auditLog,
                            InputValidator inputs) {
        this.permalinkRepo = permalinkRepo;
        this.feedRepo = feedRepo;
        this.limits = limits;
        this.ojobpubService = ojobpubService;
        this.auditLog = auditLog;
        this.inputs = inputs;
    }

    @Transactional(readOnly = true)
    public List<Permalink> findByEmployer(UUID employerId) {
        return permalinkRepo.findByEmployerIdOrderByNameAsc(employerId);
    }

    /** What points at a feed, for its screen and its delete confirmation (spec 7.12). */
    @Transactional(readOnly = true)
    public List<Permalink> pointingAt(UUID feedId) {
        return permalinkRepo.findByFeedIdOrderByNameAsc(feedId);
    }

    /** A non-member is told it does not exist (spec 2.4). */
    @Transactional(readOnly = true)
    public Permalink findVisible(UUID id, Actor actor) {
        Permalink permalink = permalinkRepo.findWithFeedById(id)
                .orElseThrow(() -> new NotFoundException("Permalink not found: " + id));
        if (!actor.isAdmin() && !actor.getEmployerIds().contains(permalink.getEmployer().getId())) {
            throw new NotFoundException("Permalink not found: " + id);
        }
        return permalink;
    }

    /** Used by the public endpoint: no user, no authorization - permalinks are public (spec 2.4). */
    @Transactional(readOnly = true)
    public Optional<Permalink> findForPublishing(UUID id) {
        return permalinkRepo.findWithFeedById(id);
    }

    /**
     * What the URL serves: its feed's document, or the employer with no jobs
     * (spec 5.5). Either way dated no earlier than the permalink's own last
     * change, so switching to an older feed still moves {@code lastUpdated}
     * forward and a cache revalidating with {@code If-Modified-Since} gets the
     * new document rather than a 304 (spec 5.4).
     */
    public OjobpubDto publish(Permalink permalink) {
        Instant since = permalink.getLastModifiedAt();
        if (permalink.getFeed() == null) {
            return ojobpubService.generateEmpty(permalink.getEmployer(), since).document();
        }
        OjobpubDto document = ojobpubService.generate(permalink.getFeed(), LocalDate.now()).document();
        if (since != null && since.isAfter(document.lastUpdated())) {
            return document.withLastUpdated(since);
        }
        return document;
    }

    @Transactional
    public Permalink save(UUID id, Employer employer, String name, String description, UUID feedId,
                          Actor actor) {
        inputs.check(new NameInput(name));
        Permalink permalink;
        if (id == null) {
            // Before the name check, as for feeds (spec 8.4).
            limits.requireRoomForPermalinks(() -> permalinkRepo.countByEmployerId(employer.getId()));
            if (permalinkRepo.existsByEmployerIdAndNameIgnoreCase(employer.getId(), name.trim())) {
                throw new ValidationFailure("name", "This employer already has a permalink with that name.");
            }
            permalink = new Permalink();
            permalink.setEmployer(employer);
        } else {
            permalink = findVisible(id, actor);
            if (!permalink.getName().equalsIgnoreCase(name.trim())
                    && permalinkRepo.existsByEmployerIdAndNameIgnoreCase(employer.getId(), name.trim())) {
                throw new ValidationFailure("name", "This employer already has a permalink with that name.");
            }
        }
        Feed before = permalink.getFeed();
        permalink.setName(name.trim());
        permalink.setDescription(description == null || description.isBlank() ? null : description.trim());
        permalink.setFeed(ownFeed(feedId, permalink.getEmployer()));
        Permalink saved = permalinkRepo.save(permalink);
        auditLog.record(AuditEvent.of(id == null ? AuditAction.PERMALINK_CREATED : AuditAction.PERMALINK_UPDATED,
                actor).in(saved.getEmployer()).target(saved.getId(), saved.getName())
                .detail(id == null ? label(saved.getFeed()) : null));
        if (id != null && !sameFeed(before, saved.getFeed())) {
            recordRetarget(saved, before, saved.getFeed(), actor);
        }
        return saved;
    }

    /**
     * The quick switch (spec 5.5): point the permalink at another of the
     * employer's feeds, or at none. Changes what the URL publishes, never the URL.
     */
    @Transactional
    public Permalink retarget(UUID id, UUID feedId, Actor actor) {
        Permalink permalink = findVisible(id, actor);
        Feed before = permalink.getFeed();
        Feed after = ownFeed(feedId, permalink.getEmployer());
        if (sameFeed(before, after)) {
            return permalink;
        }
        permalink.setFeed(after);
        // Stamped here, not left to the flush, so the served date moves at once (spec 5.4).
        permalink.setLastModifiedAt(Instant.now());
        Permalink saved = permalinkRepo.save(permalink);
        recordRetarget(saved, before, after, actor);
        return saved;
    }

    @Transactional
    public void delete(UUID id, Actor actor) {
        Permalink permalink = findVisible(id, actor);
        permalinkRepo.delete(permalink);
        auditLog.record(AuditEvent.of(AuditAction.PERMALINK_DELETED, actor).in(permalink.getEmployer())
                .target(permalink.getId(), permalink.getName()));
    }

    /**
     * A feed is about to be deleted: each permalink on it now publishes no jobs,
     * recorded so the log explains why a URL went empty (spec 5.5).
     *
     * <p>Cleared here rather than left to the database's ON DELETE SET NULL:
     * the permalinks loaded here would otherwise still point at the removed feed
     * when Hibernate flushes, and it refuses that. Clearing also stamps them, so
     * the served date moves forward. SET NULL remains for bulk deletes, such as
     * an employer's.
     */
    @Transactional
    public void feedDeleted(Feed feed, Actor actor) {
        for (Permalink permalink : permalinkRepo.findByFeedIdOrderByNameAsc(feed.getId())) {
            permalink.setFeed(null);
            permalink.setLastModifiedAt(Instant.now());
            permalinkRepo.save(permalink);
            recordRetarget(permalink, feed, null, actor);
        }
    }

    /**
     * Null for none; otherwise one of this employer's own feeds. Another
     * employer's feed is refused exactly like one that does not exist, so the
     * refusal discloses nothing (spec 3.13).
     */
    private Feed ownFeed(UUID feedId, Employer employer) {
        if (feedId == null) {
            return null;
        }
        return feedRepo.findById(feedId)
                .filter(feed -> feed.getEmployer().getId().equals(employer.getId()))
                .orElseThrow(() -> new ValidationFailure("feed", "Choose one of this employer's feeds."));
    }

    private void recordRetarget(Permalink permalink, Feed before, Feed after, Actor actor) {
        auditLog.record(AuditEvent.of(AuditAction.PERMALINK_RETARGETED, actor).in(permalink.getEmployer())
                .target(permalink.getId(), permalink.getName())
                .detail(label(before) + " → " + label(after)));
    }

    private static boolean sameFeed(Feed a, Feed b) {
        return a == null ? b == null : b != null && a.getId().equals(b.getId());
    }

    /**
     * A feed as the log names it: a dash for none. Shortened, because two names
     * and an arrow must fit the log's detail column, which holds 255.
     */
    private static String label(Feed feed) {
        if (feed == null) {
            return "—";
        }
        String name = feed.getName();
        return name.length() <= 120 ? name : name.substring(0, 119) + "…";
    }
}
