package org.letsemploy.ojobpub_publisher.feed;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.web.Scope;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.letsemploy.ojobpub_publisher.web.view.Crumb;
import org.letsemploy.ojobpub_publisher.web.view.FieldError;
import org.letsemploy.ojobpub_publisher.web.view.PageMeta;
import org.letsemploy.ojobpub_publisher.web.view.PermalinkFormView;
import org.letsemploy.ojobpub_publisher.web.view.WebserverSnippet;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.HtmlUtils;

/**
 * Permalinks, managed from the Feeds screen (spec 7.23): creating, editing,
 * switching the feed and deleting. Listed by {@link FeedController}; the rules
 * are in {@link PermalinkService}.
 */
@Controller
@RequestMapping("/feeds/permalinks")
public class PermalinkController {

    private final PermalinkService permalinkService;
    private final FeedService feedService;
    private final Scope scope;
    private final Views views;
    private final MessageSource messages;

    public PermalinkController(PermalinkService permalinkService,
                               FeedService feedService,
                               Scope scope,
                               Views views,
                               MessageSource messages) {
        this.permalinkService = permalinkService;
        this.feedService = feedService;
        this.scope = scope;
        this.views = views;
        this.messages = messages;
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        Employer employer = scope.requireActiveEmployer();
        return form(model, employer, new PermalinkFormView(null, null, null, "", null), Map.of(),
                Map.of());
    }

    @GetMapping("/{id}/update")
    public String editForm(@PathVariable UUID id, Model model) {
        Permalink permalink = permalinkService.findVisible(id, scope.user());
        return form(model, permalink.getEmployer(), new PermalinkFormView(id.toString(), permalink.getName(),
                permalink.getDescription(),
                permalink.getFeed() == null ? "" : permalink.getFeed().getId().toString(),
                views.permalinkUrl(permalink)), views.webserverSnippets(permalink), Map.of());
    }

    @PostMapping({"/create", "/{id}/update"})
    public String save(@PathVariable(required = false) UUID id,
                       @RequestParam String name,
                       @RequestParam(required = false) String description,
                       @RequestParam(required = false) String feed,
                       Model model, RedirectAttributes flash) {
        Permalink existing = id == null ? null : permalinkService.findVisible(id, scope.user());
        Employer employer = existing == null ? scope.requireActiveEmployer() : existing.getEmployer();
        try {
            permalinkService.save(id, employer, name, description, feedId(feed), scope.user());
            flash.addFlashAttribute("successMsg", "msg.success.saved");
            return "redirect:/feeds";
        } catch (ValidationFailure e) {
            // Back to the form with what was typed (spec 7.7).
            return form(model, employer, new PermalinkFormView(id == null ? null : id.toString(), name,
                    description, feed == null ? "" : feed,
                    existing == null ? null : views.permalinkUrl(existing)),
                    existing == null ? Map.of() : views.webserverSnippets(existing), e.getFieldErrors());
        }
    }

    /** The quick switch on the Feeds screen: a plain form, so it works without JavaScript. */
    @PostMapping("/{id}/target")
    public String retarget(@PathVariable UUID id, @RequestParam(required = false) String feed,
                           RedirectAttributes flash) {
        try {
            permalinkService.retarget(id, feedId(feed), scope.user());
            flash.addFlashAttribute("successMsg", "permalink.switched");
        } catch (ValidationFailure e) {
            flash.addFlashAttribute("errorMsg", "permalink.feed.invalid");
        }
        return "redirect:/feeds";
    }

    @GetMapping("/{id}/delete")
    public String deleteConfirm(@PathVariable UUID id, Model model) {
        Permalink permalink = permalinkService.findVisible(id, scope.user());
        model.addAttribute("permalink", Map.of("id", id.toString()));
        // The modal renders its body unescaped; the name is the employer's to choose.
        model.addAttribute("message", messages.getMessage("permalink.delete.body",
                new Object[]{HtmlUtils.htmlEscape(permalink.getName())}, LocaleContextHolder.getLocale()));
        return "feed/permalink_delete";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes flash) {
        permalinkService.delete(id, scope.user());
        flash.addFlashAttribute("successMsg", "msg.success.deleted");
        return "redirect:/feeds";
    }

    private String form(Model model, Employer employer, PermalinkFormView form,
                        Map<String, List<WebserverSnippet>> webserver, Map<String, String> fieldErrors) {
        String title = message(form.id() == null ? "permalink.create" : "permalink.edit");
        model.addAttribute("page", new PageMeta(title, null,
                List.of(new Crumb(message("nav.feeds"), "/feeds"), new Crumb(title, null))));
        model.addAttribute("form", form);
        model.addAttribute("webserver", webserver);
        Map<String, String> options = new LinkedHashMap<>();
        feedService.findByEmployer(employer.getId()).forEach(f -> options.put(f.getId().toString(), f.getName()));
        model.addAttribute("feedOptions", options);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("errors", fieldErrors.entrySet().stream()
                .map(en -> new FieldError(en.getKey(), en.getValue())).toList());
        return "feed/permalink_form";
    }

    /** Empty for none; anything that is not an id is refused like a stranger's feed. */
    private static UUID feedId(String feed) {
        if (feed == null || feed.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(feed.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationFailure("feed", "Choose one of this employer's feeds.");
        }
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
