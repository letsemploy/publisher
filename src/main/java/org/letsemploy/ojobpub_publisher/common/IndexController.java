package org.letsemploy.ojobpub_publisher.common;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.JobService;
import org.letsemploy.ojobpub_publisher.job.Publication;
import org.letsemploy.ojobpub_publisher.web.view.DashboardView;
import org.letsemploy.ojobpub_publisher.web.view.FeedRow;
import org.letsemploy.ojobpub_publisher.web.view.PageMeta;
import org.letsemploy.ojobpub_publisher.web.Scope;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The dashboard (spec 7.10). */
@Controller
@RequiredArgsConstructor
public class IndexController {

    private final Scope scope;
    private final JobService jobService;
    private final FeedService feedService;
    private final Views views;
    private final MessageSource messages;

    @GetMapping("/")
    public String index(Model model) {
        LocalDate today = LocalDate.now();
        int[] counts = jobService.dashboardCounts(scope.employerIds());

        List<FeedRow> feedRows = new ArrayList<>();
        for (Employer employer : scope.employers()) {
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
        model.addAttribute("dashboard",
                new DashboardView(counts[0], counts[1], counts[2], counts[3], feedRows));
        return "dashboard";
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
