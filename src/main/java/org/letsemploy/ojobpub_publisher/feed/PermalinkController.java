package org.letsemploy.ojobpub_publisher.feed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.common.validation.ApexDomain;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.manual.ManualTopic;
import org.letsemploy.ojobpub_publisher.web.FormViews;
import org.letsemploy.ojobpub_publisher.web.Scope;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.letsemploy.ojobpub_publisher.web.view.Crumb;
import org.letsemploy.ojobpub_publisher.web.view.FieldError;
import org.letsemploy.ojobpub_publisher.web.view.PageMeta;
import org.letsemploy.ojobpub_publisher.web.view.PermalinkFormView;
import org.letsemploy.ojobpub_publisher.web.view.PermalinkRow;
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
 * The Domains screen (spec 7.23): the employer's permalinks, each for one domain -
 * listing, creating, editing, switching the feed, checking the website and
 * deleting. The rules are in {@link PermalinkService}.
 */
@Controller
@RequestMapping("/domains")
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

    @GetMapping
    public String list(Model model) {
        List<PermalinkRow> rows = new ArrayList<>();
        for (Employer employer : scope.employers()) {
            // The switch offers the permalink's own employer's feeds only (spec 3.13).
            List<Feed> feeds = feedService.findByEmployer(employer.getId());
            permalinkService.findByEmployer(employer.getId()).forEach(p -> rows.add(views.permalinkRow(p, feeds)));
        }
        model.addAttribute("page", PageMeta.of(message("nav.domains")).help(ManualTopic.PERMALINKS));
        model.addAttribute("permalinks", rows);
        return "feed/domains";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        Employer employer = scope.requireActiveEmployer();
        // Most employers have given their website already; its domain is the likeliest.
        // The all feed to begin with, as for the default permalink (spec 7.23).
        String all = feedService.findByEmployer(employer.getId()).stream()
                .filter(f -> "all".equals(f.getSlug())).findFirst().map(f -> f.getId().toString()).orElse("");
        return form(model, employer, formView(null, null, null, all,
                        ApexDomain.Validator.registrable(PermalinkService.host(employer.getUrl())).orElse(null)),
                Map.of(), Map.of());
    }

    @GetMapping("/{id}/update")
    public String editForm(@PathVariable UUID id, Model model) {
        Permalink permalink = permalinkService.findVisible(id, scope.user());
        return form(model, permalink.getEmployer(), formView(permalink, permalink.getName(),
                permalink.getDescription(),
                permalink.getFeed() == null ? "" : permalink.getFeed().getId().toString(),
                permalink.getWebsiteHost()), views.webserverSnippets(permalink), Map.of());
    }

    @PostMapping({"/create", "/{id}/update"})
    public String save(@PathVariable(required = false) UUID id,
                       @RequestParam(required = false) String name,
                       @RequestParam(required = false) String description,
                       @RequestParam(required = false) String websiteHost,
                       @RequestParam(required = false) String feed,
                       Model model, RedirectAttributes flash) {
        Permalink existing = id == null ? null : permalinkService.findVisible(id, scope.user());
        Employer employer = existing == null ? scope.requireActiveEmployer() : existing.getEmployer();
        try {
            Permalink saved = permalinkService.save(id, employer, name, description, websiteHost, feedId(feed),
                    scope.user());
            flash.addFlashAttribute("successMsg", "msg.success.saved");
            // On to the next steps, configuration and check, which need the saved domain (spec 7.23).
            return FormViews.redirect("/domains/" + saved.getId() + "/update#website");
        } catch (ValidationFailure e) {
            // Back to the form with what was typed (spec 7.7).
            return form(model, employer, formView(existing, name, description, feed == null ? "" : feed,
                    websiteHost), existing == null ? Map.of() : views.webserverSnippets(existing), e.getFieldErrors());
        }
    }

    /** The quick switch on the Domains screen: a plain form, so it works without JavaScript. */
    @PostMapping("/{id}/target")
    public String retarget(@PathVariable UUID id, @RequestParam(required = false) String feed,
                           RedirectAttributes flash) {
        try {
            permalinkService.retarget(id, feedId(feed), scope.user());
            flash.addFlashAttribute("successMsg", "permalink.switched");
        } catch (ValidationFailure e) {
            flash.addFlashAttribute("errorMsg", "permalink.feed.invalid");
        }
        return "redirect:/domains";
    }

    /**
     * Asks the permalink's website what it serves (spec 7.23); the page it was
     * asked from - the list, or the edit page - then shows the answer. A plain
     * form, on the page rather than in the modal, which is itself a form.
     */
    @PostMapping("/{id}/check")
    public String check(@PathVariable UUID id, @RequestParam(required = false) String from,
                        RedirectAttributes flash) {
        try {
            permalinkService.check(id, scope.user());
        } catch (ValidationFailure e) {
            flash.addFlashAttribute("errorMsg", e.getFieldErrors().containsKey("check")
                    ? "permalink.check.wait" : "permalink.check.needsHost");
        }
        return "list".equals(from) ? "redirect:/domains" : "redirect:/domains/" + id + "/update#website";
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
        return "redirect:/domains";
    }

    private String form(Model model, Employer employer, PermalinkFormView form,
                        Map<String, List<WebserverSnippet>> webserver, Map<String, String> fieldErrors) {
        String title = message(form.id() == null ? "permalink.create" : "permalink.edit");
        model.addAttribute("page", new PageMeta(title, null,
                List.of(new Crumb(message("nav.domains"), "/domains"), new Crumb(title, null))).help(ManualTopic.PERMALINKS));
        model.addAttribute("form", form);
        model.addAttribute("webserver", webserver);
        Map<String, String> options = new LinkedHashMap<>();
        feedService.findByEmployer(employer.getId()).forEach(f -> options.put(f.getId().toString(), f.getName()));
        model.addAttribute("feedOptions", options);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("errors", fieldErrors.entrySet().stream()
                .map(en -> new FieldError(en.getKey(), en.getValue())).toList());
        return FormViews.view("feed/permalink_form");
    }

    /** The form with what was typed, and what only an existing permalink has. */
    private PermalinkFormView formView(Permalink existing, String name, String description, String feedId,
                                       String websiteHost) {
        if (existing == null) {
            return new PermalinkFormView(null, name, description, feedId, websiteHost, null, null, null, false, null);
        }
        PermalinkCheck.Result result = existing.getCheckResult();
        return new PermalinkFormView(existing.getId().toString(), name, description, feedId, websiteHost,
                views.permalinkUrl(existing), Views.websiteUrl(existing),
                result == null ? null : result.name(), result != null && result.works(),
                existing.getCheckedAt() == null ? null : Views.timestamp(existing.getCheckedAt()));
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
