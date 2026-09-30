package org.letsemploy.ojobpub_publisher.common;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.letsemploy.ojobpub_publisher.audit.AuditService;
import org.letsemploy.ojobpub_publisher.click.JobClickService;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.feed.PermalinkService;
import org.letsemploy.ojobpub_publisher.job.DashboardJobs;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.JobService;
import org.letsemploy.ojobpub_publisher.job.Publication;
import org.letsemploy.ojobpub_publisher.web.view.DashboardView;
import org.letsemploy.ojobpub_publisher.web.view.FeedRow;
import org.letsemploy.ojobpub_publisher.web.view.PageMeta;
import org.letsemploy.ojobpub_publisher.web.view.PermalinkRow;
import org.letsemploy.ojobpub_publisher.web.Scope;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The dashboard (spec 7.10). */
@Controller
public class IndexController {

    /** Rows per "needs attention" group, and events of recent activity (spec 7.10). */
    private static final int ATTENTION_ROWS = 5;
    private static final int ACTIVITY_ROWS = 5;

    private final Scope scope;
    private final JobService jobService;
    private final FeedService feedService;
    private final PermalinkService permalinkService;
    private final JobClickService jobClickService;
    private final AuditService auditService;
    private final Views views;
    private final MessageSource messages;

    public IndexController(Scope scope,
                           JobService jobService,
                           FeedService feedService,
                           PermalinkService permalinkService,
                           JobClickService jobClickService,
                           AuditService auditService,
                           Views views,
                           MessageSource messages) {
        this.scope = scope;
        this.jobService = jobService;
        this.feedService = feedService;
        this.permalinkService = permalinkService;
        this.jobClickService = jobClickService;
        this.auditService = auditService;
        this.views = views;
        this.messages = messages;
    }

    @GetMapping("/")
    public String index(Model model) {
        LocalDate today = LocalDate.now();
        DashboardJobs jobs = jobService.dashboard(scope.employerIds());

        List<FeedRow> feedRows = new ArrayList<>();
        List<PermalinkRow> permalinks = new ArrayList<>();
        for (Employer employer : scope.employers()) {
            // The URLs a website uses; a feed's own URL is for testing (spec 7.10).
            permalinkService.findByEmployer(employer.getId())
                    .forEach(p -> permalinks.add(views.permalinkRow(p, List.of())));
            for (Feed feed : feedService.findByEmployer(employer.getId())) {
                Feed loaded = feedService.findForPublishing(feed.getId()).orElse(feed);
                int published = 0;
                int excluded = 0;
                for (Job job : loaded.getJobs()) {
                    if (Publication.isPublishable(job, today)) {
                        published++;
                    } else {
                        excluded++;
                    }
                }
                feedRows.add(views.feedRow(loaded, published, excluded));
            }
        }

        model.addAttribute("page", PageMeta.of(message("nav.dashboard")));
        // Every figure covers the employers in scope, and the activity is the log
        // the Activity screen would show this actor (spec 7.10, 7.21).
        model.addAttribute("dashboard", new DashboardView(
                jobs.published(), jobs.draft(), jobs.expired(), jobs.inactive(),
                jobs.publishedShare(), jobs.newRecently(), DashboardJobs.RECENT_DAYS,
                views.clicks(jobClickService.statistics(scope.employerIds())),
                views.attention(jobs, ATTENTION_ROWS),
                auditService.latest(scope.employerIds(), scope.user(), ACTIVITY_ROWS).stream()
                        .map(views::activityRow).toList(),
                feedRows, permalinks));
        return "dashboard";
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
