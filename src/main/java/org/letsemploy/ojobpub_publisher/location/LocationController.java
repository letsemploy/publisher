package org.letsemploy.ojobpub_publisher.location;

import com.neovisionaries.i18n.CountryCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.web.view.*;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final Views views;
    private final MessageSource messages;

    @GetMapping
    public String list(Model model) {
        List<LocationRow> rows = locationService.findAll().stream()
                .map(l -> views.locationRow(l, locationService.usageCount(l.getId())))
                .toList();
        model.addAttribute("page", PageMeta.of(message("nav.locations")));
        model.addAttribute("locations", rows);
        return "location/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("page", new PageMeta(message("location.create"), null,
                List.of(new Crumb(message("nav.locations"), "/locations"),
                        new Crumb(message("location.create"), null))));
        formModel(model, new LocationFormView(null, null, null), Map.of());
        return "location/form";
    }

    @GetMapping("/{id}/update")
    public String editForm(@PathVariable UUID id, Model model) {
        Location location = locationService.findById(id);
        model.addAttribute("page", new PageMeta(message("location.edit"), null,
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
            locationService.save(id, city, country);
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
        Location location = locationService.findById(id);
        model.addAttribute("location", Map.of("id", id.toString()));
        model.addAttribute("message", messages.getMessage("location.delete.body",
                new Object[]{location.getLabel()}, LocaleContextHolder.getLocale()));
        return "location/delete";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes flash) {
        try {
            locationService.delete(id);
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
        // A native select: browsers already provide type-ahead, so no library (spec 7.8).
        Map<String, String> countries = new LinkedHashMap<>();
        java.util.Arrays.stream(CountryCode.values())
                .filter(c -> c != CountryCode.UNDEFINED && c.getAssignment() == CountryCode.Assignment.OFFICIALLY_ASSIGNED)
                .sorted(java.util.Comparator.comparing(CountryCode::getName))
                .forEach(c -> countries.put(c.getAlpha2().toUpperCase(), c.getName()));
        model.addAttribute("countryOptions", countries);
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
