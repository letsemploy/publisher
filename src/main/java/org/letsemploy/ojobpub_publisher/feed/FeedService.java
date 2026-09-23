package org.letsemploy.ojobpub_publisher.feed;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.ResourceLimits;
import org.letsemploy.ojobpub_publisher.common.Slugs;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.JobService;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.service.OjobpubService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedService {

    private final FeedRepo feedRepo;
    private final JobService jobService;
    private final ResourceLimits limits;
    private final OjobpubService ojobpubService;

    public List<Feed> findByEmployer(UUID employerId) {
        return feedRepo.findByEmployerIdOrderByNameAsc(employerId);
    }

    public Feed findVisible(UUID id, Actor user) {
        Feed feed = feedRepo.findWithJobsById(id)
                .orElseThrow(() -> new NotFoundException("Feed not found: " + id));
        if (!user.isAdmin() && !user.getEmployerIds().contains(feed.getEmployer().getId())) {
            throw new NotFoundException("Feed not found: " + id);
        }
        return feed;
    }

    /** Used by the public endpoint: no user, no authorization - feeds are public (spec 2.4). */
    public Optional<Feed> findForPublishing(UUID id) {
        return feedRepo.findWithJobsById(id);
    }

    public OjobpubService.Result publish(Feed feed) {
        return ojobpubService.generate(feed, LocalDate.now());
    }

    @Transactional
    public Feed save(UUID id, Employer employer, String name, String slug, String description) {
        if (name == null || name.isBlank()) {
            throw new ValidationFailure("name", "A name is required.");
        }
        Feed feed;
        if (id == null) {
            // Before the name check: an employer at its cap should not be told
            // to pick a different name (spec 8.4).
            limits.requireRoomForFeeds(() -> feedRepo.countByEmployerId(employer.getId()));
            feed = new Feed();
            feed.setEmployer(employer);
            if (feedRepo.existsByEmployerIdAndNameIgnoreCase(employer.getId(), name.trim())) {
                throw new ValidationFailure("name", "This employer already has a feed with that name.");
            }
        } else {
            feed = feedRepo.findById(id)
                    .orElseThrow(() -> new NotFoundException("Feed not found: " + id));
        }
        feed.setName(name.trim());
        feed.setSlug(Slugs.slugify(slug == null || slug.isBlank() ? name : slug, "feed"));
        feed.setDescription(description == null || description.isBlank() ? null : description.trim());
        return feedRepo.save(feed);
    }

    /**
     * Every employer gets a working URL the moment it exists: an {@code all} feed
     * is created with the employer (spec 3.5).
     *
     * <p>Deliberately not routed through {@link #save}, and deliberately not
     * subject to the feed quota: an employer without its {@code all} feed is a
     * broken employer. Do not "tidy" this into save() - the exemption is
     * structural, and {@code theDefaultFeedIsNeverRefused} guards it.
     */
    @Transactional
    public Feed createDefaultFeed(Employer employer) {
        Feed feed = new Feed();
        feed.setEmployer(employer);
        feed.setName("All jobs");
        feed.setSlug("all");
        feed.setDescription("Every job this employer currently advertises.");
        return feedRepo.save(feed);
    }

    /** Membership is restricted to the feed's own employer (spec 3.5). */
    @Transactional
    public Feed addJob(UUID feedId, UUID jobId, Actor user) {
        Feed feed = findVisible(feedId, user);
        Job job = jobService.findVisible(jobId, user);
        if (!job.getEmployer().getId().equals(feed.getEmployer().getId())) {
            throw new ValidationFailure("job", "That job belongs to a different employer.");
        }
        feed.getJobs().add(job);
        return feedRepo.save(feed);
    }

    @Transactional
    public Feed removeJob(UUID feedId, UUID jobId, Actor user) {
        Feed feed = findVisible(feedId, user);
        feed.getJobs().removeIf(j -> j.getId().equals(jobId));
        return feedRepo.save(feed);
    }

    @Transactional
    public void delete(UUID id, Actor user) {
        feedRepo.delete(findVisible(id, user));
    }

    /** Jobs of the same employer not yet in this feed (spec 7.12). */
    public List<Job> candidates(Feed feed, String query) {
        List<UUID> memberIds = feed.getJobs().stream().map(Job::getId).toList();
        return jobService.findByEmployer(feed.getEmployer().getId()).stream()
                .filter(j -> !memberIds.contains(j.getId()))
                .filter(j -> query == null || query.isBlank()
                        || j.getTitle().toLowerCase().contains(query.toLowerCase()))
                .toList();
    }
}
