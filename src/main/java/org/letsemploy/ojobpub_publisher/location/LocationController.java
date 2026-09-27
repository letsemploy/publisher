package org.letsemploy.ojobpub_publisher.location;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.web.Scope;
import org.letsemploy.ojobpub_publisher.web.view.*;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The employer's locations (spec 7.14): the list covers the employers in scope
 * (spec 2.5), a new one belongs to the active employer, and every other route goes
 * through {@link LocationService#findVisible}, so another employer's location is a
 * 404 (spec 2.4).
 */
@Controller
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final Scope scope;
    private final Views views;
    private final MessageSource messages;

    @GetMapping
    public String list(Model model) {
        List<LocationRow> rows = locationService.visibleTo(scope.employerIds()).stream()
                .map(l -> views.locationRow(l, locationService.usageCount(l.getId())))
                .toList();
        model.addAttribute("page", PageMeta.of(message("nav.locations")));
        model.addAttribute("locations", rows);
        // Several employers' locations side by side need saying whose each is.
        model.addAttribute("showEmployer", !scope.hasSingleEmployer());
        return "location/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        Employer employer = scope.requireActiveEmployer();
        model.addAttribute("page", new PageMeta(message("location.create"), employer.getName(),
                List.of(new Crumb(message("nav.locations"), "/locations"),
                        new Crumb(message("location.create"), null))));
        formModel(model, new LocationFormView(null, null, null), Map.of());
        return "location/form";
    }

    @GetMapping("/{id}/update")
    public String editForm(@PathVariable UUID id, Model model) {
        Location location = locationService.findVisible(id, scope.user());
        model.addAttribute("page", new PageMeta(message("location.edit"), location.getEmployer().getName(),
                List.of(new Crumb(message("nav.locations"), "/locations"),
                        new Crumb(location.getLabel(), null))));
        formModel(model, new LocationFormView(location.getId().toString(), location.getCity(),
                location.getCountryCode()), Map.of());
        return "location/form";
    }

    @PostMapping({"/create", "/{id}/update"})
    public String save(@PathVariable(required = false) UUID id,
                       @RequestParam String city,
                       @RequestParam String country,
                       Model model, RedirectAttributes flash) {
        try {
            if (id == null) {
                locationService.create(scope.requireActiveEmployer(), city, country, scope.user());
            } else {
                locationService.update(id, city, country, scope.user());
            }
            flash.addFlashAttribute("successMsg", "msg.success.saved");
            return "redirect:/locations";
        } catch (ValidationFailure e) {
            model.addAttribute("page", PageMeta.of(
                    id == null ? message("location.create") : message("location.edit")));
            formModel(model, new LocationFormView(id == null ? null : id.toString(), city, country),
                    e.getFieldErrors());
            return "location/form";
        }
    }

    @GetMapping("/{id}/delete")
    public String deleteConfirm(@PathVariable UUID id, Model model) {
        Location location = locationService.findVisible(id, scope.user());
        model.addAttribute("location", Map.of("id", id.toString()));
        model.addAttribute("message", messages.getMessage("location.delete.body",
                new Object[]{location.getLabel()}, LocaleContextHolder.getLocale()));
        return "location/delete";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes flash) {
        try {
            locationService.delete(id, scope.user());
            flash.addFlashAttribute("successMsg", "msg.success.deleted");
        } catch (ValidationFailure e) {
            flash.addFlashAttribute("errorMsg", "location.inUse");
        }
        return "redirect:/locations";
    }

    private void formModel(Model model, LocationFormView form, Map<String, String> fieldErrors) {
        model.addAttribute("form", form);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("errors", fieldErrors.entrySet().stream()
                .map(e -> new FieldError(e.getKey(), e.getValue())).toList());
        model.addAttribute("countryOptions", Countries.options());
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
