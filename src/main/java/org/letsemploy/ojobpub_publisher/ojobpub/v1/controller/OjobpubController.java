package org.letsemploy.ojobpub_publisher.ojobpub.v1.controller;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.dto.OjobpubDto;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/**
 * The public feed endpoint (spec 5.1):
 * {@code /ojobpub/v1/{employerSlug}_{employerId}/{feedSlug}_{feedId}/ojobpub.json}
 *
 * <p>Anonymous, cacheable, GET only. Identity lives in the UUID; the slug is
 * decorative, so a stale slug still resolves and redirects to the canonical URL.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class OjobpubController {

    /**
     * Slug and UUID are separated by an underscore - the one character that can
     * appear in neither side - so the split is unambiguous however many hyphens a
     * slug contains (spec 5.1).
     */
    private static final Pattern SEGMENT = Pattern.compile(
            "^(?<slug>[a-z0-9-]+)_(?<id>[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
                    + "-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");

    private final FeedService feedService;

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

        // URLs repair themselves: an old link keeps working and is nudged onward.
        String canonicalEmployer = feed.getEmployer().getUrlSegment();
        String canonicalFeed = feed.getUrlSegment();
        if (!canonicalEmployer.equals(employerSegment) || !canonicalFeed.equals(feedSegment)) {
            String canonical = "/ojobpub/v1/" + canonicalEmployer + "/" + canonicalFeed + "/ojobpub.json";
            RedirectView redirect = new RedirectView(canonical);
            redirect.setStatusCode(org.springframework.http.HttpStatus.MOVED_PERMANENTLY);
            return ResponseEntity.status(org.springframework.http.HttpStatus.MOVED_PERMANENTLY)
                    .header("Location", canonical).build();
        }

        OjobpubDto document = feedService.publish(feed).getDocument();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .lastModified(document.getLastUpdated())
                .body(document);
    }

    /** Feed callers are machines, so errors are JSON and never an HTML page (spec 8.2). */
    private ResponseEntity<?> notFound() {
        return ResponseEntity.status(404)
                .contentType(MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("error", "not_found",
                        "message", "No such feed."));
    }
}
