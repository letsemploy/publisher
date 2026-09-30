package org.letsemploy.ojobpub_publisher.web;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.click.JobClick;
import org.letsemploy.ojobpub_publisher.click.JobClickService;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.feed.Permalink;
import org.letsemploy.ojobpub_publisher.job.DashboardJobs;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.JobStatusEvent;
import org.letsemploy.ojobpub_publisher.invitation.Invitation;
import org.letsemploy.ojobpub_publisher.job.Publication;
import org.letsemploy.ojobpub_publisher.location.Location;
import org.letsemploy.ojobpub_publisher.membership.Membership;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.token.ServiceToken;
import org.letsemploy.ojobpub_publisher.token.TokenLifecycle;
import org.letsemploy.ojobpub_publisher.tag.Tag;
import org.letsemploy.ojobpub_publisher.web.view.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Turns entities into the view models the templates bind to. Kept in one place so
 * a screen and the published document cannot disagree about a job's status.
 */
@Component
public class Views {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final MessageSource messages;
    /** Feed URLs must be absolute and correct behind a reverse proxy (spec 9.5). */
    private final String baseUrl;

    public Views(MessageSource messages, @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.messages = messages;
        this.baseUrl = baseUrl;
    }

    /** An instant as every screen shows one. */
    public static String timestamp(Instant instant) {
        return TIMESTAMP.format(instant);
    }

    public String feedUrl(Feed feed) {
        return baseUrl + "/ojobpub/v1/" + feed.getEmployer().getUrlSegment()
                + "/" + feed.getUrlSegment() + "/ojobpub.json";
    }

    /** A permalink's URL: the id alone, since it names no one feed (spec 5.5). */
    public String permalinkUrl(Permalink permalink) {
        return baseUrl + "/ojobpub/v1/permalink/" + permalink.getId() + "/ojobpub.json";
    }

    /**
     * A permalink as the Feeds screen lists it.
     *
     * @param feeds the employer's feeds, for the switch
     */
    public PermalinkRow permalinkRow(Permalink permalink, List<Feed> feeds) {
        Map<String, String> options = new LinkedHashMap<>();
        feeds.forEach(f -> options.put(f.getId().toString(), f.getName()));
        Feed feed = permalink.getFeed();
        return new PermalinkRow(permalink.getId().toString(), permalink.getName(),
                permalink.getDescription(),
                feed == null ? null : feed.getId().toString(), feed == null ? null : feed.getName(),
                permalinkUrl(permalink), options);
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
                .map(r -> new ReadinessCheck(r.labelKey(), r.satisfied(), r.fixAnchor()))
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

    /**
     * An audit event in words (spec 7.21). The labels are the snapshots taken when
     * it happened, so a renamed or deleted thing is named as it was then.
     */
    public ActivityRow activityRow(AuditEvent e) {
        String detail = e.getDetail();
        if (detail != null && e.getAction().hasRoleDetail()) {
            // "EDITOR → OWNER" in the reader's language.
            detail = Arrays.stream(detail.split(" → "))
                    .map(role -> message("role." + role.toLowerCase()))
                    .collect(Collectors.joining(" → "));
        }
        String text = messages.getMessage("audit.action." + e.getAction().name(),
                new Object[]{e.getTargetLabel(), detail == null ? "—" : detail}, LocaleContextHolder.getLocale());
        String actor = e.getActorType() == AuditEvent.ActorType.SYSTEM
                ? message("audit.actor.system") : e.getActorLabel();
        return new ActivityRow(TIMESTAMP.format(e.getOccurredAt()), actor,
                e.getActorType().name().toLowerCase(), text, e.getEmployerLabel());
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
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

    /** The dashboard's clicks (spec 7.10), in the viewer's language. */
    public ClicksView clicks(JobClickService.Statistics statistics) {
        Locale locale = LocaleContextHolder.getLocale();
        long topJob = statistics.topJobs().stream().mapToLong(JobClickService.JobClicks::clicks).max().orElse(0);
        long topCountry = statistics.countries().stream().mapToLong(JobClickService.CountryClicks::clicks).max().orElse(0);
        List<ClicksView.Job> jobs = statistics.topJobs().stream()
                .map(c -> new ClicksView.Job(c.job().getId().toString(), c.job().getTitle(),
                        c.clicks(), share(c.clicks(), topJob),
                        sparkline(c.recent()), c.recentTotal(),
                        trend(c.recentTotal(), c.previous(), JobClickService.TREND_DAYS, locale)))
                .toList();
        List<ClicksView.Country> countries = statistics.countries().stream()
                .map(c -> new ClicksView.Country(c.country(), countryName(c.country(), locale),
                        c.clicks(), share(c.clicks(), topCountry)))
                .toList();

        List<Long> perDay = statistics.daily().stream().map(JobClickService.Day::clicks).toList();
        long busiest = perDay.stream().mapToLong(Long::longValue).max().orElse(0);
        DateTimeFormatter day = DateTimeFormatter.ofPattern("d MMM", locale);
        List<ClicksView.Block> blocks = statistics.daily().stream()
                .map(d -> new ClicksView.Block(level(d.clicks(), busiest),
                        messages.getMessage("dashboard.clicks.day", new Object[]{day.format(d.day()), d.clicks()}, locale)))
                .toList();
        long activeDays = perDay.stream().filter(c -> c > 0).count();
        String summary = messages.getMessage("dashboard.clicks.strip",
                new Object[]{statistics.days(), activeDays, busiest}, locale);

        return new ClicksView(statistics.days(), JobClickService.TREND_DAYS, statistics.total(),
                trend(statistics.total(), statistics.previousTotal(), statistics.days(), locale),
                sparkline(perDay), blocks, summary, jobs, countries);
    }

    /** The values as Tabler's sparkline reads them: "3,0,5". */
    private static String sparkline(List<Long> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** A day's activity against the busiest day, as a tracking block's colour; none is no colour. */
    private static String level(long clicks, long busiest) {
        if (clicks == 0 || busiest == 0) {
            return "";
        }
        double ratio = (double) clicks / busiest;
        return ratio <= 1.0 / 3 ? "bg-azure-lt" : ratio <= 2.0 / 3 ? "bg-azure" : "bg-blue";
    }

    /** Now against the same number of days before, in words (spec 7.9). */
    private ClicksView.Trend trend(long now, long before, int days, Locale locale) {
        if (before == 0) {
            return now == 0
                    ? new ClicksView.Trend("flat", messages.getMessage("dashboard.trend.none", new Object[]{days}, locale))
                    : new ClicksView.Trend("new", messages.getMessage("dashboard.trend.new", new Object[]{days}, locale));
        }
        long percent = Math.round((now - before) * 100.0 / before);
        String direction = percent > 0 ? "up" : percent < 0 ? "down" : "flat";
        return new ClicksView.Trend(direction, messages.getMessage("dashboard.trend." + direction,
                new Object[]{Math.abs(percent), days}, locale));
    }

    /** The dashboard's "needs attention" card (spec 7.10). */
    public AttentionView attention(DashboardJobs jobs, int shown) {
        Locale locale = LocaleContextHolder.getLocale();
        LocalDate today = LocalDate.now();
        List<AttentionView.Row> closing = jobs.closingSoon().stream().limit(shown)
                .map(c -> row(c.job(), closes(ChronoUnit.DAYS.between(today, c.lastDay()), locale)))
                .toList();
        List<AttentionView.Row> incomplete = jobs.incomplete().stream().limit(shown)
                .map(j -> row(j, messages.getMessage("dashboard.attention.incomplete.note", null, locale)))
                .toList();
        Instant now = Instant.now();
        List<AttentionView.Row> stale = jobs.staleDrafts().stream().limit(shown)
                .map(j -> row(j, messages.getMessage("dashboard.attention.stale.note",
                        new Object[]{Duration.between(j.getLastModifiedAt(), now).toDays()}, locale)))
                .toList();
        return new AttentionView(
                new AttentionView.Group(closing, jobs.closingSoon().size() - closing.size(), "/jobs?status=published"),
                new AttentionView.Group(incomplete, jobs.incomplete().size() - incomplete.size(), "/jobs?status=incomplete"),
                new AttentionView.Group(stale, jobs.staleDrafts().size() - stale.size(), "/jobs?status=draft"));
    }

    private String closes(long days, Locale locale) {
        if (days <= 0) {
            return messages.getMessage("dashboard.attention.closing.today", null, locale);
        }
        if (days == 1) {
            return messages.getMessage("dashboard.attention.closing.tomorrow", null, locale);
        }
        return messages.getMessage("dashboard.attention.closing.days", new Object[]{days}, locale);
    }

    private static AttentionView.Row row(Job job, String note) {
        return new AttentionView.Row(job.getId().toString(), job.getTitle(), note);
    }

    private String countryName(String code, Locale locale) {
        if (JobClick.UNKNOWN_COUNTRY.equals(code)) {
            return messages.getMessage("dashboard.clicks.unknownCountry", null, locale);
        }
        return Locale.of("", code).getDisplayCountry(locale);
    }

    private static int share(long value, long max) {
        return max == 0 ? 0 : (int) Math.round(value * 100.0 / max);
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

    public InvitationRow invitationRow(Invitation invitation) {
        return new InvitationRow(invitation.getId().toString(),
                invitation.getEmployer().getName(),
                invitation.getRole().name().toLowerCase(),
                invitation.getInvitedByLabel(),
                TIMESTAMP.format(invitation.getCreatedAt()));
    }

    public PendingInvitationRow pendingInvitationRow(Invitation invitation) {
        return new PendingInvitationRow(invitation.getId().toString(),
                displayName(invitation.getInvitee()),
                invitation.getInvitee().getEmail(),
                invitation.getRole().name().toLowerCase(),
                invitation.getInvitedByLabel(),
                TIMESTAMP.format(invitation.getCreatedAt()));
    }

    public MemberRow memberRow(Membership membership, boolean lastOwner, UUID viewerId) {
        UserEntity user = membership.getUser();
        return new MemberRow(user.getId().toString(), displayName(user), user.getEmail(),
                membership.getRole().name().toLowerCase(), lastOwner,
                membership.isSuspended() ? TIMESTAMP.format(membership.getSuspendedAt()) : null,
                user.getId().equals(viewerId));
    }

    public TokenRow tokenRow(ServiceToken token, Instant now, int warningDays) {
        return new TokenRow(token.getId().toString(), token.getName(), token.getPrefix(),
                token.getScopes().stream().map(s -> s.name().toLowerCase()).sorted().toList(),
                displayName(token.getCreatedBy()),
                TIMESTAMP.format(token.getCreatedAt()),
                token.getLastUsedAt() == null ? null : TIMESTAMP.format(token.getLastUsedAt()),
                token.getExpiresAt() == null ? null : TIMESTAMP.format(token.getExpiresAt()),
                TokenLifecycle.state(token, now, warningDays).name().toLowerCase(),
                token.isRevoked());
    }

    private String displayName(UserEntity user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return user.getEmail() != null ? user.getEmail() : user.getSubject();
    }

    public Ref ref(Employer employer) {
        return new Ref(employer.getId().toString(), employer.getName());
    }

    private String text(LocalDate date) {
        return date == null ? null : date.toString();
    }
}
