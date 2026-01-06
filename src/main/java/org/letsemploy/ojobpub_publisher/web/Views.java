package org.letsemploy.ojobpub_publisher.web;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.JobStatusEvent;
import org.letsemploy.ojobpub_publisher.job.Publication;
import org.letsemploy.ojobpub_publisher.location.Location;
import org.letsemploy.ojobpub_publisher.tag.Tag;
import org.letsemploy.ojobpub_publisher.web.view.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Turns entities into the view models the templates bind to. Kept in one place so
 * a screen and the published document cannot disagree about a job's status.
 */
@Component
@RequiredArgsConstructor
public class Views {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** Feed URLs must be absolute and correct behind a reverse proxy (spec 9.5). */
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public String feedUrl(Feed feed) {
        return baseUrl + "/ojobpub/v1/" + feed.getEmployer().getUrlSegment()
                + "/" + feed.getUrlSegment() + "/ojobpub.json";
    }

    public PublicationStatus status(Job job, LocalDate today) {
        return switch (Publication.presentation(job, today)) {
            case PUBLISHED -> PublicationStatus.PUBLISHED;
            case EXPIRED -> PublicationStatus.EXPIRED;
            case INCOMPLETE -> PublicationStatus.INCOMPLETE;
            case DRAFT -> PublicationStatus.DRAFT;
            case INACTIVE -> PublicationStatus.INACTIVE;
        };
    }

    public JobRow jobRow(Job job, long feedCount, LocalDate today) {
        return new JobRow(job.getId().toString(), job.getTitle(), status(job, today),
                job.getJobType().name().toLowerCase(),
                job.getLocations().stream().map(Location::getCity).sorted().toList(),
                (int) feedCount, job.getReferenceId());
    }

    public JobDetailView jobDetail(Job job, List<FeedMembership> feeds,
                                   List<JobStatusEvent> history, LocalDate today) {
        List<ReadinessCheck> readiness = Publication.requirements(job).stream()
                .map(r -> new ReadinessCheck(r.getLabelKey(), r.isSatisfied(), r.getFixAnchor()))
                .toList();
        return new JobDetailView(
                job.getId().toString(), job.getTitle(), job.getDescription(), job.getUrl(),
                job.getLanguageCode(), job.getReferenceId(), job.getCategory(),
                job.getJobType().name().toLowerCase(),
                job.getWorkType() == null ? null : job.getWorkType().name().toLowerCase(),
                job.getExperienceLevel() == null ? null : job.getExperienceLevel().name().toLowerCase(),
                workLoad(job), salary(job),
                text(job.getPublishedAt()), text(job.getStartDate()),
                text(job.getEndDate()), text(job.getApplyBefore()),
                status(job, today),
                job.getLocations().stream().map(Location::getLabel).sorted().toList(),
                job.getTags().stream().map(Tag::getName).sorted().toList(),
                readiness, feeds,
                history.stream().map(this::event).toList(),
                job.getStatus().allowedTransitions().stream().map(Enum::name).toList());
    }

    private TransitionEvent event(JobStatusEvent e) {
        return new TransitionEvent(
                e.getFromStatus() == null ? "-" : e.getFromStatus().name(),
                e.getToStatus().name(), e.getActor(), TIMESTAMP.format(e.getOccurredAt()));
    }

    private String workLoad(Job job) {
        if (!job.hasWorkLoad()) {
            return null;
        }
        Integer min = job.getWorkLoadPercentMin();
        Integer max = job.getWorkLoadPercentMax();
        if (min != null && max != null) {
            return min + "–" + max + "%";
        }
        return (min != null ? "from " + min : "up to " + max) + "%";
    }

    private String salary(Job job) {
        if (!job.hasSalaryAmount()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (job.getSalaryCurrency() != null) {
            sb.append(job.getSalaryCurrency()).append(' ');
        }
        if (job.getSalaryMin() != null && job.getSalaryMax() != null) {
            sb.append(job.getSalaryMin().toPlainString()).append('–')
                    .append(job.getSalaryMax().toPlainString());
        } else if (job.getSalaryMin() != null) {
            sb.append("from ").append(job.getSalaryMin().toPlainString());
        } else {
            sb.append("up to ").append(job.getSalaryMax().toPlainString());
        }
        if (job.getSalaryInterval() != null) {
            sb.append(' ').append(job.getSalaryInterval().name().toLowerCase());
        }
        return sb.toString();
    }

    public FeedRow feedRow(Feed feed, int publishedCount, int excludedCount) {
        return new FeedRow(feed.getId().toString(), feed.getName(), feed.getSlug(),
                feedUrl(feed), publishedCount, feed.getJobs().size(),
                feed.getLastModifiedAt() == null ? "-" : TIMESTAMP.format(feed.getLastModifiedAt()),
                excludedCount);
    }

    public EmployerRow employerRow(Employer employer, long jobCount, long feedCount) {
        return new EmployerRow(employer.getId().toString(), employer.getName(), employer.getSlug(),
                employer.getIndustry(), employer.getHeadquarters().getLabel(),
                (int) jobCount, (int) feedCount);
    }

    public LocationRow locationRow(Location location, long usages) {
        return new LocationRow(location.getId().toString(), location.getCity(),
                location.getCountryCode(), location.getCountry().getName(), (int) usages);
    }

    public TagRow tagRow(Tag tag, long jobCount) {
        return new TagRow(String.valueOf(tag.getId()), tag.getName(), (int) jobCount);
    }

    public Ref ref(Employer employer) {
        return new Ref(employer.getId().toString(), employer.getName());
    }

    private String text(LocalDate date) {
        return date == null ? null : date.toString();
    }
}
