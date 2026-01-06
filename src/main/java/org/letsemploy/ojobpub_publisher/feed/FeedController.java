package org.letsemploy.ojobpub_publisher.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.JobService;
import org.letsemploy.ojobpub_publisher.job.Publication;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.service.OjobpubService;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.service.OjobpubValidator;
import org.letsemploy.ojobpub_publisher.web.view.*;
import org.letsemploy.ojobpub_publisher.web.Scope;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/feeds")
@RequiredArgsConstructor
@Slf4j
public class FeedController {

    private final FeedService feedService;
    private final JobService jobService;
    private final OjobpubValidator validator;
    private final Scope scope;
    private final Views views;
    private final MessageSource messages;
    private final ObjectMapper objectMapper;

    @GetMapping
    public String list(Model model) {
        LocalDate today = LocalDate.now();
        List<FeedRow> rows = new ArrayList<>();
        for (Employer employer : scope.employers()) {
            for (Feed feed : feedService.findByEmployer(employer.getId())) {
                Feed loaded = feedService.findForPublishing(feed.getId()).orElse(feed);
                int published = (int) loaded.getJobs().stream()
                        .filter(j -> Publication.isPublishable(j, today)).count();
                rows.add(views.feedRow(loaded, published, loaded.getJobs().size() - published));
            }
        }
        model.addAttribute("page", PageMeta.of(message("nav.feeds")));
        model.addAttribute("feeds", rows);
        return "feed/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model,
                         @RequestParam(required = false) String q) {
        model.addAttribute("feed", detailView(id, q));
        Feed feed = feedService.findVisible(id, scope.user());
        model.addAttribute("page", new PageMeta(feed.getName(), null,
                List.of(new Crumb(message("nav.feeds"), "/feeds"), new Crumb(feed.getName(), null))));
        return "feed/detail";
    }

    private FeedDetailView detailView(UUID id, String query) {
        Feed feed = feedService.findVisible(id, scope.user());
        LocalDate today = LocalDate.now();
        OjobpubService.Result result = feedService.publish(feed);

        List<JobRow> members = feed.getJobs().stream()
                .sorted(java.util.Comparator.comparing(Job::getTitle))
                .map(j -> views.jobRow(j, jobService.countFeeds(j.getId()), today))
                .toList();
        List<JobRow> candidates = feedService.candidates(feed, query).stream()
                .map(j -> views.jobRow(j, jobService.countFeeds(j.getId()), today))
                .toList();
        List<FeedExclusion> exclusions = result.getExclusions().stream()
                .map(x -> new FeedExclusion(x.getJobId(), x.getJobTitle(), x.getReasonKey()))
                .toList();

        return new FeedDetailView(feed.getId().toString(), feed.getName(), feed.getSlug(),
                feed.getDescription(), views.feedUrl(feed), members, candidates, exclusions,
                prettyPrint(result.getDocument()), validator.isValid(result.getDocument()));
    }

    /** The preview shows exactly what the public URL serves (spec 7.12). */
    private String prettyPrint(Object document) {
        try {
            ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter();
            return writer.writeValueAsString(document);
        } catch (Exception e) {
            log.error("Could not render the feed preview", e);
            return "{}";
        }
    }

    @GetMapping("/{id}/candidates")
    public String candidates(@PathVariable UUID id, @RequestParam(required = false) String q,
                             Model model) {
        model.addAttribute("feed", detailView(id, q));
        return "feed/fragments/candidates :: candidates";
    }

    @PostMapping("/{id}/jobs/{jobId}/add")
    public String addJob(@PathVariable UUID id, @PathVariable UUID jobId, Model model) {
        feedService.addJob(id, jobId, scope.user());
        model.addAttribute("feed", detailView(id, null));
        return "feed/fragments/panels :: panels";
    }

    @PostMapping("/{id}/jobs/{jobId}/remove")
    public String removeJob(@PathVariable UUID id, @PathVariable UUID jobId, Model model) {
        feedService.removeJob(id, jobId, scope.user());
        model.addAttribute("feed", detailView(id, null));
        return "feed/fragments/panels :: panels";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        Employer employer = scope.requireActiveEmployer();
        model.addAttribute("page", new PageMeta(message("feed.create"), null,
                List.of(new Crumb(message("nav.feeds"), "/feeds"),
                        new Crumb(message("feed.create"), null))));
        model.addAttribute("form", new FeedFormView(null, null, null, null,
                views.feedUrl(previewFeed(employer, "new-feed"))));
        model.addAttribute("errors", List.of());
        model.addAttribute("fieldErrors", Map.of());
        return "feed/form";
    }

    /** A throwaway instance purely to render the resulting URL live on the form. */
    private Feed previewFeed(Employer employer, String slug) {
        Feed feed = new Feed();
        feed.setEmployer(employer);
        feed.setSlug(slug);
        return feed;
    }

    @GetMapping("/{id}/update")
    public String editForm(@PathVariable UUID id, Model model) {
        Feed feed = feedService.findVisible(id, scope.user());
        model.addAttribute("page", new PageMeta(message("feed.edit"), null,
                List.of(new Crumb(message("nav.feeds"), "/feeds"),
                        new Crumb(feed.getName(), "/feeds/" + id))));
        model.addAttribute("form", new FeedFormView(feed.getId().toString(), feed.getName(),
                feed.getSlug(), feed.getDescription(), views.feedUrl(feed)));
        model.addAttribute("errors", List.of());
        model.addAttribute("fieldErrors", Map.of());
        return "feed/form";
    }

    @PostMapping({"/create", "/{id}/update"})
    public String save(@PathVariable(required = false) UUID id,
                       @RequestParam String name,
                       @RequestParam(required = false) String slug,
                       @RequestParam(required = false) String description,
                       Model model, RedirectAttributes flash) {
        Employer employer = id == null
                ? scope.requireActiveEmployer()
                : feedService.findVisible(id, scope.user()).getEmployer();
        try {
            Feed saved = feedService.save(id, employer, name, slug, description);
            flash.addFlashAttribute("successMsg", "msg.success.saved");
            return "redirect:/feeds/" + saved.getId();
        } catch (ValidationFailure e) {
            model.addAttribute("page", PageMeta.of(
                    id == null ? message("feed.create") : message("feed.edit")));
            model.addAttribute("form", new FeedFormView(id == null ? null : id.toString(),
                    name, slug, description, views.feedUrl(previewFeed(employer,
                    org.letsemploy.ojobpub_publisher.common.Slugs.slugify(
                            slug == null || slug.isBlank() ? name : slug, "feed")))));
            model.addAttribute("fieldErrors", e.getFieldErrors());
            model.addAttribute("errors", e.getFieldErrors().entrySet().stream()
                    .map(en -> new FieldError(en.getKey(), en.getValue())).toList());
            return "feed/form";
        }
    }

    @GetMapping("/{id}/delete")
    public String deleteConfirm(@PathVariable UUID id, Model model) {
        Feed feed = feedService.findVisible(id, scope.user());
        model.addAttribute("feed", Map.of("id", id.toString()));
        model.addAttribute("message", messages.getMessage("feed.delete.body",
                new Object[]{feed.getName()}, LocaleContextHolder.getLocale()));
        return "feed/delete";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes flash) {
        feedService.delete(id, scope.user());
        flash.addFlashAttribute("successMsg", "msg.success.deleted");
        return "redirect:/feeds";
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
