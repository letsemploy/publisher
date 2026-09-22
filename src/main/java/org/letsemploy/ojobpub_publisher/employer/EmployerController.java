package org.letsemploy.ojobpub_publisher.employer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.job.JobService;
import org.letsemploy.ojobpub_publisher.location.Location;
import org.letsemploy.ojobpub_publisher.location.LocationService;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.web.view.*;
import org.letsemploy.ojobpub_publisher.web.Scope;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/employers")
@RequiredArgsConstructor
public class EmployerController {

    private final EmployerService employerService;
    private final LocationService locationService;
    private final FeedService feedService;
    private final MembershipService membershipService;
    private final JobService jobService;
    private final Scope scope;
    private final Views views;
    private final MessageSource messages;

    @GetMapping
    public String list(Model model, @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page) {
        Page<Employer> result = employerService.pageVisibleTo(scope.user(), q,
                PageRequest.of(Math.max(page, 0), 20));
        List<EmployerRow> rows = result.getContent().stream()
                .map(e -> views.employerRow(e,
                        jobService.findByEmployer(e.getId()).size(),
                        feedService.findByEmployer(e.getId()).size()))
                .toList();
        model.addAttribute("page", PageMeta.of(message("nav.employers")));
        model.addAttribute("employers",
                new PageView<>(rows, result.getNumber(), 20, result.getTotalElements(), "/employers"));
        return "employer/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        Employer employer = employerService.findVisible(id, scope.user());
        model.addAttribute("page", new PageMeta(employer.getName(), null,
                List.of(new Crumb(message("nav.employers"), "/employers"),
                        new Crumb(employer.getName(), null))));
        model.addAttribute("employer", views.employerRow(employer,
                jobService.findByEmployer(id).size(), feedService.findByEmployer(id).size()));
        // Owners and admins may edit the record and manage people (spec 2.1).
        model.addAttribute("canAdminister", membershipService.canAdminister(scope.user(), id));
        return "employer/detail";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("page", new PageMeta(message("employer.create"), null,
                List.of(new Crumb(message("nav.employers"), "/employers"),
                        new Crumb(message("employer.create"), null))));
        formModel(model, new EmployerFormView(null, null, null, null, null, null), Map.of());
        return "employer/form";
    }

    @GetMapping("/{id}/update")
    public String editForm(@PathVariable UUID id, Model model) {
        membershipService.requireOwner(scope.user(), id);
        Employer employer = employerService.findVisible(id, scope.user());
        model.addAttribute("page", new PageMeta(message("employer.edit"), null,
                List.of(new Crumb(message("nav.employers"), "/employers"),
                        new Crumb(employer.getName(), "/employers/" + id))));
        formModel(model, new EmployerFormView(employer.getId().toString(), employer.getName(),
                employer.getSlug(), employer.getUrl(), employer.getIndustry(),
                employer.getHeadquarters().getId().toString()), Map.of());
        return "employer/form";
    }

    @PostMapping({"/create", "/{id}/update"})
    public String save(@PathVariable(required = false) UUID id,
                       @RequestParam String name,
                       @RequestParam(required = false) String slug,
                       @RequestParam(required = false) String url,
                       @RequestParam(required = false) String industry,
                       @RequestParam(required = false) String headquarters,
                       Model model, RedirectAttributes flash) {
        try {
            UUID locationId = headquarters == null || headquarters.isBlank()
                    ? null : UUID.fromString(headquarters);
            Employer saved = employerService.save(id, name, slug, url, industry, locationId,
                    scope.user());
            // Every employer gets a working URL the moment it exists (spec 3.5).
            if (id == null) {
                feedService.createDefaultFeed(saved);
            }
            flash.addFlashAttribute("successMsg", "msg.success.saved");
            return "redirect:/employers/" + saved.getId();
        } catch (ValidationFailure e) {
            model.addAttribute("page", PageMeta.of(
                    id == null ? message("employer.create") : message("employer.edit")));
            formModel(model, new EmployerFormView(id == null ? null : id.toString(),
                    name, slug, url, industry, headquarters), e.getFieldErrors());
            return "employer/form";
        }
    }

    private void formModel(Model model, EmployerFormView form, Map<String, String> fieldErrors) {
        model.addAttribute("form", form);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("errors", fieldErrors.entrySet().stream()
                .map(e -> new FieldError(e.getKey(), e.getValue())).toList());
        Map<String, String> locations = new LinkedHashMap<>();
        for (Location location : locationService.findAll()) {
            locations.put(location.getId().toString(), location.getLabel());
        }
        model.addAttribute("locationOptions", locations);
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
