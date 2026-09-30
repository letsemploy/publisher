package org.letsemploy.ojobpub_publisher.ojobpub.v1.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.feed.Permalink;
import org.letsemploy.ojobpub_publisher.feed.PermalinkService;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.dto.OjobpubDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public feed endpoint (spec 5.1):
 * {@code /ojobpub/v1/{employerSlug}_{employerId}/{feedSlug}_{feedId}/ojobpub.json},
 * and the permalink endpoint (spec 5.5): {@code /ojobpub/v1/permalink/{id}/ojobpub.json}.
 *
 * <p>Anonymous, cacheable, GET only. Identity lives in the UUID; the slug is
 * decorative, so a stale slug still resolves and redirects to the canonical URL.
 */
@RestController
public class OjobpubController {

    private static final Logger log = LoggerFactory.getLogger(OjobpubController.class);

    /**
     * Slug and UUID are separated by an underscore - the one character that can
     * appear in neither side - so the split is unambiguous however many hyphens a
     * slug contains (spec 5.1).
     */
    private static final Pattern SEGMENT = Pattern.compile(
            "^(?<slug>[a-z0-9-]+)_(?<id>[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
                    + "-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");

    /** A bare UUID: a permalink has no slug, since it names no one feed (spec 5.5). */
    private static final Pattern PERMALINK = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final FeedService feedService;
    private final PermalinkService permalinkService;

    public OjobpubController(FeedService feedService, PermalinkService permalinkService) {
        this.feedService = feedService;
        this.permalinkService = permalinkService;
    }

    @GetMapping(value = "/ojobpub/v1/{employerSegment}/{feedSegment}/ojobpub.json",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @CrossOrigin(origins = "*")
    public ResponseEntity<?> publish(@PathVariable String employerSegment,
                                     @PathVariable String feedSegment) {
        Matcher employerMatch = SEGMENT.matcher(employerSegment);
        Matcher feedMatch = SEGMENT.matcher(feedSegment);
        // A malformed segment reveals nothing about what does or does not exist.
        if (!employerMatch.matches() || !feedMatch.matches()) {
            return notFound();
        }

        UUID employerId = UUID.fromString(employerMatch.group("id").toLowerCase());
        UUID feedId = UUID.fromString(feedMatch.group("id").toLowerCase());

        Optional<Feed> found = feedService.findForPublishing(feedId);
        if (found.isEmpty() || !found.get().getEmployer().getId().equals(employerId)) {
            return notFound();
        }
        Feed feed = found.get();

        String canonicalEmployer = feed.getEmployer().getUrlSegment();
        String canonicalFeed = feed.getUrlSegment();
        if (!canonicalEmployer.equals(employerSegment) || !canonicalFeed.equals(feedSegment)) {
            return movedTo("/ojobpub/v1/" + canonicalEmployer + "/" + canonicalFeed + "/ojobpub.json");
        }
        return ok(feedService.publish(feed).document());
    }

    /**
     * A permalink (spec 5.5): the document of whichever feed it points at, or the
     * employer with no jobs. Served here, never redirected, so a consumer that
     * does not follow redirects works and the feed's own URL is never handed out.
     */
    @GetMapping(value = "/ojobpub/v1/permalink/{id}/ojobpub.json",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @CrossOrigin(origins = "*")
    public ResponseEntity<?> permalink(@PathVariable String id) {
        if (!PERMALINK.matcher(id).matches()) {
            return notFound();
        }
        UUID permalinkId = UUID.fromString(id.toLowerCase());
        Optional<Permalink> found = permalinkService.findForPublishing(permalinkId);
        if (found.isEmpty()) {
            return notFound();
        }
        // One spelling per resource, as for a feed (spec 5.1).
        if (!id.equals(permalinkId.toString())) {
            return movedTo("/ojobpub/v1/permalink/" + permalinkId + "/ojobpub.json");
        }
        return ok(permalinkService.publish(found.get()));
    }

    private static ResponseEntity<?> ok(OjobpubDto document) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .lastModified(document.lastUpdated())
                .body(document);
    }

    /** URLs repair themselves: an old link keeps working and is nudged onward. */
    private static ResponseEntity<?> movedTo(String canonical) {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).header("Location", canonical).build();
    }

    /**
     * A failure while serving: JSON like every other answer here, and handled here
     * rather than by the back-office error page, which builds the shell and would
     * open a session (spec 5.1, 8.2). The detail goes to the log only.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> failed(Exception e, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unexpected error [{}] serving {}", correlationId, request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .body(java.util.Map.of("error", "internal_error",
                        "message", "The feed could not be served.",
                        "reference", correlationId));
    }

    /** Feed callers are machines, so errors are JSON and never an HTML page (spec 8.2). */
    private ResponseEntity<?> notFound() {
        return ResponseEntity.status(404)
                .contentType(MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("error", "not_found",
                        "message", "No such feed."));
    }
}
