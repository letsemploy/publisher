package org.letsemploy.ojobpub_publisher.tag;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HxRequest;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;
    private final Views views;
    private final MessageSource messages;

    @GetMapping
    public String list(Model model, @RequestParam(required = false) String q) {
        populate(model, q);
        model.addAttribute("page", PageMeta.of(message("nav.tags")));
        return "tag/list";
    }

    @HxRequest(boosted = false)
    @GetMapping
    public String listFragment(Model model, @RequestParam(required = false) String q) {
        populate(model, q);
        return "tag/fragments/table :: table";
    }

    private void populate(Model model, String q) {
        List<TagRow> rows = tagService.search(q).stream()
                .map(t -> views.tagRow(t, tagService.jobCount(t.getId())))
                .toList();
        model.addAttribute("tags", rows);
        model.addAttribute("query", q);
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("page", new PageMeta(message("tag.create"), null,
                List.of(new Crumb(message("nav.tags"), "/tags"),
                        new Crumb(message("tag.create"), null))));
        formModel(model, new TagFormView(null, null), Map.of());
        return "tag/form";
    }

    @GetMapping("/{id}/update")
    public String editForm(@PathVariable Long id, Model model) {
        Tag tag = tagService.findById(id);
        model.addAttribute("page", new PageMeta(message("tag.edit"), null,
                List.of(new Crumb(message("nav.tags"), "/tags"), new Crumb(tag.getName(), null))));
        formModel(model, new TagFormView(String.valueOf(tag.getId()), tag.getName()), Map.of());
        return "tag/form";
    }

    @PostMapping({"/create", "/{id}/update"})
    public String save(@PathVariable(required = false) Long id, @RequestParam String name,
                       Model model, RedirectAttributes flash) {
        try {
            tagService.save(id, name);
            flash.addFlashAttribute("successMsg", "msg.success.saved");
            return "redirect:/tags";
        } catch (ValidationFailure e) {
            model.addAttribute("page", PageMeta.of(
                    id == null ? message("tag.create") : message("tag.edit")));
            formModel(model, new TagFormView(id == null ? null : String.valueOf(id), name),
                    e.getFieldErrors());
            return "tag/form";
        }
    }

    @GetMapping("/{id}/delete")
    public String deleteConfirm(@PathVariable Long id, Model model) {
        Tag tag = tagService.findById(id);
        model.addAttribute("tag", Map.of("id", String.valueOf(id)));
        model.addAttribute("message", messages.getMessage("tag.delete.body",
                new Object[]{tag.getName(), tagService.jobCount(id)},
                LocaleContextHolder.getLocale()));
        return "tag/delete";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        tagService.delete(id);
        flash.addFlashAttribute("successMsg", "msg.success.deleted");
        return "redirect:/tags";
    }

    private void formModel(Model model, TagFormView form, Map<String, String> fieldErrors) {
        model.addAttribute("form", form);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("errors", fieldErrors.entrySet().stream()
                .map(e -> new FieldError(e.getKey(), e.getValue())).toList());
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
