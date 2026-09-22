package org.letsemploy.ojobpub_publisher.job;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.location.Location;
import org.letsemploy.ojobpub_publisher.location.LocationService;
import org.letsemploy.ojobpub_publisher.tag.Tag;
import org.letsemploy.ojobpub_publisher.tag.TagService;
import org.letsemploy.ojobpub_publisher.web.view.*;
import org.letsemploy.ojobpub_publisher.web.Scope;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobController {

    /** Pagination is capped regardless of what the request asks for (spec 8.3). */
    private static final int MAX_PAGE_SIZE = 100;

    private static final List<String> SORTABLE = List.of("title", "publishedAt", "lastModifiedAt");

    private final JobService jobService;
    private final FeedService feedService;
    private final TagService tagService;
    private final LocationService locationService;
    private final Scope scope;
    private final Views views;
    private final MessageSource messages;

    // ------------------------------------------------------------------ list

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String jobType,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(defaultValue = "title") String sort) {
        populateList(model, q, status, jobType, page, size, sort);
        model.addAttribute("page", PageMeta.of(message("nav.jobs")));
        return "job/list";
    }

    /** The same URL, swapped as a fragment when htmx asks for one (spec 9.2). */
    @HxRequest(boosted = false)
    @GetMapping
    public String listFragment(Model model,
                               @RequestParam(required = false) String q,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String jobType,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size,
                               @RequestParam(defaultValue = "title") String sort) {
        populateList(model, q, status, jobType, page, size, sort);
        return "job/fragments/table :: table";
    }

    private void populateList(Model model, String q, String status, String jobType,
                              int page, int size, String sort) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String sortField = SORTABLE.contains(sort) ? sort : "title";
        Publication.Presentation statusFilter = presentationFilter(status);
        JobType typeFilter = jobType == null || jobType.isBlank()
                ? null : JobType.valueOf(jobType.toUpperCase());

        Page<Job> result = jobService.search(scope.employerIds(), q, statusFilter, typeFilter,
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by(sortField)));

        // An out-of-range page clamps to the last available page rather than erroring (spec 8.3).
        int pageNumber = result.getTotalPages() > 0 && page >= result.getTotalPages()
                ? result.getTotalPages() - 1 : Math.max(page, 0);
        if (pageNumber != page) {
            result = jobService.search(scope.employerIds(), q, statusFilter, typeFilter,
                    PageRequest.of(pageNumber, pageSize, Sort.by(sortField)));
        }

        LocalDate today = LocalDate.now();
        // One query for the whole page, not one per row (spec 10).
        Map<UUID, Long> feedCounts = jobService.feedCounts(result.getContent());
        List<JobRow> rows = result.getContent().stream()
                .map(j -> views.jobRow(j, feedCounts.getOrDefault(j.getId(), 0L), today))
                .toList();

        model.addAttribute("jobs", new PageView<>(rows, pageNumber, pageSize,
                result.getTotalElements(), listUri(q, status, jobType, pageSize, sortField)));
        model.addAttribute("filter", new JobFilterView(q, status, jobType, sortField));
        model.addAttribute("statuses",
                List.of("published", "expired", "incomplete", "draft", "inactive"));
        model.addAttribute("jobTypes",
                java.util.Arrays.stream(JobType.values()).map(t -> t.name().toLowerCase()).toList());
    }

    private String listUri(String q, String status, String jobType, int size, String sort) {
        StringBuilder sb = new StringBuilder("/jobs?size=").append(size).append("&sort=").append(sort);
        if (q != null && !q.isBlank()) {
            sb.append("&q=").append(java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (status != null && !status.isBlank()) {
            sb.append("&status=").append(status);
        }
        if (jobType != null && !jobType.isBlank()) {
            sb.append("&jobType=").append(jobType);
        }
        return sb.toString();
    }

    /**
     * The list filters on what a job *presents* as (spec 7.6). PUBLISHED, EXPIRED
     * and INCOMPLETE are all stored as ACTIVE, so the filter is evaluated against
     * the publication rules rather than the stored column - mapping them to ACTIVE
     * here would make all three return the same rows.
     *
     * <p>An unrecognised value filters nothing rather than erroring, so a stale
     * bookmark degrades to the unfiltered list (spec 8.3).
     */
    private Publication.Presentation presentationFilter(String presentation) {
        if (presentation == null || presentation.isBlank()) {
            return null;
        }
        try {
            return Publication.Presentation.valueOf(presentation.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- detail

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        Job job = jobService.findVisible(id, scope.user());
        LocalDate today = LocalDate.now();

        PublicationStatus presented = views.status(job, today);
        String reason = Publication.presentation(job, today).reasonKey();
        List<FeedMembership> memberships = new ArrayList<>();
        for (UUID feedId : jobService.feedIdsOf(id)) {
            feedService.findForPublishing(feedId).ifPresent(feed -> memberships.add(
                    new FeedMembership(feed.getId().toString(), feed.getName(), presented, reason)));
        }

        model.addAttribute("page", new PageMeta(job.getTitle(), null,
                List.of(new Crumb(message("nav.jobs"), "/jobs"), new Crumb(job.getTitle(), null))));
        model.addAttribute("job",
                views.jobDetail(job, memberships, jobService.history(id), today));
        return "job/detail";
    }

    // ------------------------------------------------------------------ form

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("page", new PageMeta(message("job.create"), null,
                List.of(new Crumb(message("nav.jobs"), "/jobs"),
                        new Crumb(message("job.create"), null))));
        JobForm form = new JobForm();
        form.setLanguage("en");
        form.setJobType("permanent");
        formModel(model, form, Map.of(), List.of(), List.of());
        return "job/form";
    }

    @GetMapping("/{id}/update")
    public String editForm(@PathVariable UUID id, Model model) {
        Job job = jobService.findVisible(id, scope.user());
        model.addAttribute("page", new PageMeta(message("job.edit"), null,
                List.of(new Crumb(message("nav.jobs"), "/jobs"),
                        new Crumb(job.getTitle(), "/jobs/" + id))));
        formModel(model, JobForm.of(job), Map.of(),
                job.getTags().stream().map(t -> new Ref(String.valueOf(t.getId()), t.getName())).toList(),
                job.getLocations().stream()
                        .map(l -> new Ref(l.getId().toString(), l.getLabel())).toList());
        return "job/form";
    }

    @PostMapping({"/create", "/{id}/update"})
    public String save(@PathVariable(required = false) UUID id,
                       @ModelAttribute JobForm form,
                       @RequestParam(defaultValue = "draft") String action,
                       Model model,
                       RedirectAttributes flash) {
        form.setId(id);
        Employer employer = id == null
                ? scope.requireActiveEmployer()
                : jobService.findVisible(id, scope.user()).getEmployer();
        try {
            Job saved = jobService.save(form, employer, "activate".equals(action),
                    scope.user().getDisplayName());
            flash.addFlashAttribute("successMsg", "msg.success.saved");
            return "redirect:/jobs/" + saved.getId();
        } catch (ValidationFailure e) {
            // Re-render with every entered value preserved (spec 7.7).
            model.addAttribute("page", new PageMeta(
                    id == null ? message("job.create") : message("job.edit"), null, List.of()));
            formModel(model, form, e.getFieldErrors(), refsForTags(form), refsForLocations(form));
            return "job/form";
        }
    }

    private List<Ref> refsForTags(JobForm form) {
        List<Ref> refs = new ArrayList<>();
        for (Long tagId : form.getTags()) {
            Tag tag = tagService.findById(tagId);
            refs.add(new Ref(String.valueOf(tag.getId()), tag.getName()));
        }
        return refs;
    }

    private List<Ref> refsForLocations(JobForm form) {
        List<Ref> refs = new ArrayList<>();
        for (UUID locationId : form.getLocations()) {
            Location location = locationService.findById(locationId);
            refs.add(new Ref(location.getId().toString(), location.getLabel()));
        }
        return refs;
    }

    private void formModel(Model model, JobForm form, Map<String, String> fieldErrors,
                           List<Ref> tagChips, List<Ref> locationChips) {
        model.addAttribute("form", form);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("errors", fieldErrors.entrySet().stream()
                .map(e -> new FieldError(e.getKey(), e.getValue())).toList());
        model.addAttribute("tagChips", tagChips);
        model.addAttribute("locationChips", locationChips);
        model.addAttribute("languages", Map.of("en", "English", "de", "Deutsch", "fr", "Francais"));
        model.addAttribute("jobTypeOptions", options("jobType.", JobType.values()));
        model.addAttribute("workTypeOptions", options("workType.", WorkType.values()));
        model.addAttribute("experienceOptions", options("experienceLevel.", ExperienceLevel.values()));
        model.addAttribute("intervalOptions", options("salaryInterval.", SalaryInterval.values()));
    }

    private Map<String, String> options(String prefix, Enum<?>[] values) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Enum<?> value : values) {
            String key = value.name().toLowerCase();
            out.put(key, message(prefix + key));
        }
        return out;
    }

    // ------------------------------------------------------- status & delete

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable UUID id, @RequestParam String to,
                               RedirectAttributes flash) {
        try {
            jobService.transition(id, JobStatus.valueOf(to.toUpperCase()), scope.user());
            flash.addFlashAttribute("successMsg", "msg.success.saved");
        } catch (ValidationFailure e) {
            flash.addFlashAttribute("errorMsg", "job.readiness.blocked");
        }
        return "redirect:/jobs/" + id;
    }

    @GetMapping("/{id}/delete")
    public String deleteConfirm(@PathVariable UUID id, Model model) {
        Job job = jobService.findVisible(id, scope.user());
        model.addAttribute("job", Map.of("id", id.toString()));
        model.addAttribute("message", messages.getMessage("job.delete.body",
                new Object[]{job.getTitle(), jobService.countFeeds(id)},
                LocaleContextHolder.getLocale()));
        return "job/delete";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes flash) {
        jobService.delete(id, scope.user());
        flash.addFlashAttribute("successMsg", "msg.success.deleted");
        return "redirect:/jobs";
    }

    // ------------------------------------------------------------- pickers

    @GetMapping("/tags/search")
    public String searchTags(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("results", tagService.search(q).stream()
                .map(t -> new Ref(String.valueOf(t.getId()), t.getName())).toList());
        model.addAttribute("query", q);
        model.addAttribute("targetName", "tags");
        return "fragments/picker :: results";
    }

    @GetMapping("/locations/search")
    public String searchLocations(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("results", locationService.search(q).stream()
                .map(l -> new Ref(l.getId().toString(), l.getLabel())).toList());
        model.addAttribute("query", q);
        model.addAttribute("targetName", "locations");
        return "fragments/picker :: results";
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
