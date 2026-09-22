package org.letsemploy.ojobpub_publisher.api;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.api.ApiTypes.*;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.invitation.Invitation;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.JobService;
import org.letsemploy.ojobpub_publisher.job.Publication;
import org.letsemploy.ojobpub_publisher.location.Location;
import org.letsemploy.ojobpub_publisher.membership.Membership;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.tag.Tag;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Entities to API types (spec 11.3).
 *
 * <p>A job's {@code presentation} comes from {@link Publication}, the same rule
 * the screens and the published feed read, so the API cannot report a job as
 * published that the feed omits.
 */
@Component
@RequiredArgsConstructor
public class ApiMapper {

    private final Views views;
    private final JobService jobService;
    private final FeedService feedService;
    private final EmployerService employerService;
    private final MembershipService membershipService;

    // --- read and map -------------------------------------------------------
    //
    // A mutation must not run inside a transaction of its own. A domain refusal
    // travels as a ValidationFailure, and a service that throws one inside the
    // caller's transaction marks it rollback-only; the caller then catches the
    // failure, returns its userErrors, and the commit fails anyway with an
    // UnexpectedRollbackException - a fault, exactly what spec 11.4 says a
    // refusal must not be. So each mutation lets the service own the write, and
    // reads the result back here for mapping.

    @Transactional(readOnly = true)
    public JobDto readJob(UUID id, Actor actor) {
        return job(jobService.findVisible(id, actor), LocalDate.now());
    }

    @Transactional(readOnly = true)
    public FeedDto readFeed(UUID id, Actor actor) {
        return feed(feedService.findVisible(id, actor), LocalDate.now());
    }

    @Transactional(readOnly = true)
    public EmployerDto readEmployer(UUID id, Actor actor) {
        return employer(employerService.findVisible(id, actor));
    }

    @Transactional(readOnly = true)
    public MemberDto readMember(UUID employerId, UUID userId) {
        return membershipService.membersOf(employerId).stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .findFirst()
                .map(this::member)
                .orElse(null);
    }

    public LocationDto location(Location location) {
        return new LocationDto(location.getId().toString(), location.getCity(),
                location.getCountryCode());
    }

    public TagDto tag(Tag tag) {
        return new TagDto(tag.getId().toString(), tag.getName());
    }

    public EmployerDto employer(Employer employer) {
        return new EmployerDto(employer.getId().toString(), employer.getName(), employer.getSlug(),
                employer.getUrl(), employer.getIndustry(),
                employer.getHeadquarters() == null ? null : location(employer.getHeadquarters()));
    }

    public JobDto job(Job job, LocalDate today) {
        return new JobDto(
                job.getId().toString(), job.getTitle(), job.getDescription(), job.getUrl(),
                job.getLanguageCode(), job.getReferenceId(), job.getCategory(),
                name(job.getJobType()), name(job.getWorkType()), name(job.getExperienceLevel()),
                job.getWorkLoadPercentMin(), job.getWorkLoadPercentMax(),
                job.getSalaryMin(), job.getSalaryMax(), job.getSalaryCurrency(),
                name(job.getSalaryInterval()), date(job.getStartDate()), date(job.getEndDate()),
                date(job.getApplyBefore()), name(job.getStatus()), date(job.getPublishedAt()),
                Publication.presentation(job, today).name(),
                job.getLocations().stream()
                        .sorted(Comparator.comparing(Location::getCity))
                        .map(this::location).toList(),
                job.getTags().stream().map(Tag::getName).sorted().toList());
    }

    public FeedDto feed(Feed feed, LocalDate today) {
        return new FeedDto(feed.getId().toString(), feed.getName(), feed.getSlug(),
                feed.getDescription(), views.feedUrl(feed),
                feed.getJobs().stream().map(job -> job(job, today)).toList());
    }

    public MemberDto member(Membership membership) {
        return new MemberDto(membership.getUser().getId().toString(),
                membership.getUser().getDisplayName(), membership.getUser().getEmail(),
                membership.getRole().name());
    }

    public InvitationDto invitation(Invitation invitation) {
        return new InvitationDto(invitation.getId().toString(),
                invitation.getInvitee().getEmail(), invitation.getRole().name(),
                invitation.getStatus().name(), invitation.getInvitedByLabel());
    }

    public List<JobDto> jobs(List<Job> jobs, LocalDate today) {
        return jobs.stream().map(job -> job(job, today)).toList();
    }

    /** Dates are rendered as plain ISO strings; the schema declares them String. */
    private static String date(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
