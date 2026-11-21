package org.letsemploy.ojobpub_publisher.feed;

import java.net.IDN;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditLog;
import org.letsemploy.ojobpub_publisher.common.ResourceLimits;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.common.validation.HostInput;
import org.letsemploy.ojobpub_publisher.common.validation.InputValidator;
import org.letsemploy.ojobpub_publisher.common.validation.NameInput;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.job.Publication;
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
    private final PermalinkCheck website;

    /** When each person last checked a website: one check per {@link #CHECK_INTERVAL}. */
    private final Map<UUID, Instant> lastCheck = new ConcurrentHashMap<>();
    static final Duration CHECK_INTERVAL = Duration.ofSeconds(5);

    public PermalinkService(PermalinkRepo permalinkRepo,
                            FeedRepo feedRepo,
                            ResourceLimits limits,
                            OjobpubService ojobpubService,
                            AuditLog auditLog,
                            InputValidator inputs,
                            PermalinkCheck website) {
        this.permalinkRepo = permalinkRepo;
        this.feedRepo = feedRepo;
        this.limits = limits;
        this.ojobpubService = ojobpubService;
        this.auditLog = auditLog;
        this.inputs = inputs;
        this.website = website;
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
        if (!actor.canActOn(permalink.getEmployer().getId())) {
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
        OjobpubDto document = ojobpubService.generate(permalink.getFeed(), Publication.today()).document();
        if (since != null && since.isAfter(document.lastUpdated())) {
            return document.withLastUpdated(since);
        }
        return document;
    }

    @Transactional
    public Permalink save(UUID id, Employer employer, String name, String description, String websiteHost,
                          UUID feedId, Actor actor) {
        inputs.check(new NameInput(name));
        String host = host(websiteHost);
        inputs.check(new HostInput(host));
        Permalink permalink;
        if (id == null) {
            // Only for an actor who may work on the employer, as for feeds (spec 2.4).
            if (!actor.canActOn(employer.getId())) {
                throw new NotFoundException("Employer not found: " + employer.getId());
            }
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
        String nameBefore = permalink.getName();
        String descriptionBefore = permalink.getDescription();
        String hostBefore = permalink.getWebsiteHost();
        permalink.setName(name.trim());
        permalink.setDescription(description == null || description.isBlank() ? null : description.trim());
        permalink.setFeed(ownFeed(feedId, permalink.getEmployer()));
        if (!Objects.equals(hostBefore, host)) {
            // What was found on another website says nothing about this one.
            permalink.setWebsiteHost(host);
            permalink.setCheckedAt(null);
            permalink.setCheckResult(null);
        }
        Permalink saved = permalinkRepo.save(permalink);
        // A new target is recorded as a retarget below; this row is for the rest.
        if (id == null || !Objects.equals(nameBefore, saved.getName())
                || !Objects.equals(descriptionBefore, saved.getDescription())
                || !Objects.equals(hostBefore, saved.getWebsiteHost())) {
            auditLog.record(AuditEvent.of(id == null ? AuditAction.PERMALINK_CREATED : AuditAction.PERMALINK_UPDATED,
                    actor).in(saved.getEmployer()).target(saved.getId(), saved.getName())
                    .detail(id == null ? label(saved.getFeed()) : null));
        }
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

    /**
     * Asks the permalink's website what it serves (spec 7.23) and keeps the
     * answer. Not transactional: the request may take seconds, and no connection
     * is held while it does. The result is written without moving the
     * permalink's date, which dates the served document (spec 5.5).
     *
     * @throws ValidationFailure {@code websiteHost} when there is none, or
     *         {@code check} when this person checked a moment ago - the check
     *         asks a host someone typed, so it must not become a scanner
     */
    public PermalinkCheck.Result check(UUID id, Actor actor) {
        Permalink permalink = findVisible(id, actor);
        if (permalink.getWebsiteHost() == null) {
            throw new ValidationFailure("websiteHost", "Enter your website's domain first.");
        }
        Instant now = Instant.now();
        lastCheck.values().removeIf(at -> at.isBefore(now.minus(CHECK_INTERVAL)));
        if (lastCheck.putIfAbsent(actor.getId(), now) != null) {
            throw new ValidationFailure("check", "Wait a moment before checking again.");
        }
        PermalinkCheck.Result result = website.check(permalink.getWebsiteHost(), permalink.getId());
        permalinkRepo.recordCheck(permalink.getId(), permalink.getWebsiteHost(), result, Instant.now());
        return result;
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

    /**
     * The host of what was typed: a whole URL is accepted and cut down to it,
     * lower-cased and in ASCII. Null for nothing; anything left that is no host
     * name is refused by {@link HostInput}.
     */
    static String host(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String host = input.trim();
        int scheme = host.indexOf("://");
        if (scheme >= 0) {
            host = host.substring(scheme + 3);
        }
        for (char end : new char[] {'/', '?', '#'}) {
            int at = host.indexOf(end);
            if (at >= 0) {
                host = host.substring(0, at);
            }
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        try {
            host = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED);
        } catch (IllegalArgumentException e) {
            // Left as typed, for HostInput to refuse.
        }
        return host.toLowerCase(Locale.ROOT);
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
