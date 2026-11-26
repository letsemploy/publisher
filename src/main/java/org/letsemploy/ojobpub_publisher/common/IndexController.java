package org.letsemploy.ojobpub_publisher.common;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpSession;
import org.letsemploy.ojobpub_publisher.audit.AuditService;
import org.letsemploy.ojobpub_publisher.click.JobClickService;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.feed.Permalink;
import org.letsemploy.ojobpub_publisher.feed.PermalinkService;
import org.letsemploy.ojobpub_publisher.job.DashboardJobs;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.JobService;
import org.letsemploy.ojobpub_publisher.job.Publication;
import org.letsemploy.ojobpub_publisher.manual.ManualTopic;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
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

    /** Set once this session's first dashboard visit has asked for the domain (spec 7.23). */
    static final String DOMAIN_ASKED = IndexController.class.getName() + ".domainAsked";

    private final Scope scope;
    private final JobService jobService;
    private final FeedService feedService;
    private final PermalinkService permalinkService;
    private final JobClickService jobClickService;
    private final AuditService auditService;
    private final Views views;
    private final MessageSource messages;
    private final CurrentUserService currentUserService;

    public IndexController(Scope scope,
                           JobService jobService,
                           FeedService feedService,
                           PermalinkService permalinkService,
                           JobClickService jobClickService,
                           AuditService auditService,
                           Views views,
                           MessageSource messages,
                           CurrentUserService currentUserService) {
        this.scope = scope;
        this.currentUserService = currentUserService;
        this.jobService = jobService;
        this.feedService = feedService;
        this.permalinkService = permalinkService;
        this.jobClickService = jobClickService;
        this.auditService = auditService;
        this.views = views;
        this.messages = messages;
    }

    @GetMapping("/")
    public String index(Model model, HttpSession session) {
        // Every sign-in lands here; the one that created the account lands on the
        // Welcome instead (spec 7.30). Nothing is stored, so it happens once.
        if (currentUserService.accountCreatedNow()) {
            return "redirect:/welcome";
        }
        Optional<String> askDomain = askForDomain(session);
        if (askDomain.isPresent()) {
            return "redirect:" + askDomain.get();
        }
        LocalDate today = Publication.today();
        DashboardJobs jobs = jobService.dashboard(scope.employerIds());

        List<FeedRow> feedRows = new ArrayList<>();
        List<PermalinkRow> permalinks = new ArrayList<>();
        for (Employer employer : scope.employers()) {
            // The URLs a website uses; a feed's own URL is for testing (spec 7.10).
            List<Permalink> own = permalinkService.findByEmployer(employer.getId());
            own.forEach(p -> permalinks.add(views.permalinkRow(p, List.of())));
            Set<UUID> onDomain = PermalinkService.feedsOnADomain(own);
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
                feedRows.add(views.feedRow(loaded, published, excluded, onDomain.contains(feed.getId())));
            }
        }

        // Getting started (spec 7.10): what a newcomer has yet to create. Not for
        // admin mode, which is the staff's view, not a newcomer's.
        Actor actor = scope.user();
        boolean hasEmployer = scope.activeEmployer().isPresent();
        boolean hintEmployer = !actor.isAdmin() && !hasEmployer;
        boolean hintJob = !actor.isAdmin() && hasEmployer && jobs.total() == 0;
        boolean hintDomain = !actor.isAdmin() && hasEmployer
                && permalinks.stream().noneMatch(PermalinkRow::checkWorks);

        model.addAttribute("page", PageMeta.of(message("nav.dashboard")).help(ManualTopic.DASHBOARD));
        // Every figure covers the employers in scope, and the activity is the log
        // the Activity screen would show this actor (spec 7.10, 7.21).
        model.addAttribute("dashboard", new DashboardView(
                jobs.published(), jobs.draft(), jobs.expired(), jobs.inactive(),
                jobs.publishedShare(), jobs.newRecently(), DashboardJobs.RECENT_DAYS,
                views.clicks(jobClickService.statistics(scope.employerIds())),
                views.attention(jobs, ATTENTION_ROWS),
                auditService.latest(scope.employerIds(), scope.user(), ACTIVITY_ROWS).stream()
                        .map(views::activityRow).toList(),
                feedRows, permalinks, hintEmployer, hintJob, hintDomain));
        return "dashboard";
    }

    /**
     * The first dashboard visit of a session - where every sign-in lands - asks
     * for the domain instead, while the active employer has a permalink without
     * one, or none at all (spec 7.23). Once per session, so the dashboard is the
     * next page whatever the person does. Not in admin mode, which is the staff's
     * view, nor while viewing as someone, which is read-only (spec 2.9, 2.10).
     */
    private Optional<String> askForDomain(HttpSession session) {
        if (session.getAttribute(DOMAIN_ASKED) != null || scope.user().isAdmin()
                || currentUserService.isImpersonating()) {
            return Optional.empty();
        }
        Optional<Employer> employer = scope.activeEmployer();
        if (employer.isEmpty()) {
            return Optional.empty();
        }
        session.setAttribute(DOMAIN_ASKED, Boolean.TRUE);
        List<Permalink> permalinks = permalinkService.findByEmployer(employer.get().getId());
        if (permalinks.isEmpty()) {
            return Optional.of("/domains/create");
        }
        return permalinks.stream().filter(p -> p.getWebsiteHost() == null).findFirst()
                .map(p -> "/domains/" + p.getId() + "/update");
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
