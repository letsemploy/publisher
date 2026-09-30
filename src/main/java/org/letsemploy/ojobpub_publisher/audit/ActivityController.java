package org.letsemploy.ojobpub_publisher.audit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.letsemploy.ojobpub_publisher.web.Scope;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.letsemploy.ojobpub_publisher.web.view.ActivityRow;
import org.letsemploy.ojobpub_publisher.web.view.Crumb;
import org.letsemploy.ojobpub_publisher.web.view.PageMeta;
import org.letsemploy.ojobpub_publisher.web.view.PageView;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The audit log's three views (spec 7.21, 7.22). Who may read what is decided in
 * {@link AuditService}; this only picks which log the screen is about.
 */
@Controller
public class ActivityController {

    private final AuditService auditService;
    private final Scope scope;
    private final Views views;
    private final UserRepo userRepo;
    private final MessageSource messages;

    public ActivityController(AuditService auditService,
                              Scope scope,
                              Views views,
                              UserRepo userRepo,
                              MessageSource messages) {
        this.auditService = auditService;
        this.scope = scope;
        this.views = views;
        this.userRepo = userRepo;
        this.messages = messages;
    }

    /** The employers in scope (spec 2.5), as the actor may read their logs. */
    @GetMapping("/activity")
    public String employers(@RequestParam(defaultValue = "0") int page, Model model) {
        Actor actor = scope.user();
        Optional<Employer> active = scope.activeEmployer();
        model.addAttribute("page", PageMeta.of(message("nav.activity"),
                active.map(Employer::getName).orElse(null)));
        model.addAttribute("offerEverything", actor.isAdmin());
        return render(model, auditService.forEmployers(scope.employerIds(), actor, page), "/activity",
                active.isEmpty());
    }

    /**
     * Everything, for platform staff: events about people, which belong to no
     * employer, and the logs of employers since deleted. A route of its own rather
     * than the switcher's "All employers", which a single-employer installation
     * never offers (spec 2.5).
     */
    @GetMapping("/activity/all")
    public String everything(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<AuditEvent> events = auditService.everything(scope.user(), page);
        model.addAttribute("page", new PageMeta(message("activity.everything"), null,
                List.of(new Crumb(message("nav.activity"), "/activity"),
                        new Crumb(message("activity.everything"), null))));
        return render(model, events, "/activity/all", true);
    }

    /** What was done to me, and what I did (spec 7.22). */
    @GetMapping("/activity/mine")
    public String mine(@RequestParam(defaultValue = "0") int page, Model model) {
        Actor actor = scope.user();
        Page<AuditEvent> events = auditService.forPerson(actor.getId(), actor, page);
        model.addAttribute("page", PageMeta.of(message("activity.mine")));
        return render(model, events, "/activity/mine", true);
    }

    /** One person's own log, for platform staff, from the Users screen (spec 7.20). */
    @GetMapping("/users/{id}/activity")
    public String person(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page, Model model) {
        // Refused first, so a non-admin learns nothing about the id.
        Page<AuditEvent> events = auditService.forPerson(id, scope.user(), page);
        UserEntity user = userRepo.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
        model.addAttribute("page", new PageMeta(message("nav.activity"), user.getLabel(),
                List.of(new Crumb(message("nav.users"), "/users"), new Crumb(user.getLabel(), null))));
        return render(model, events, "/users/" + id + "/activity", true);
    }

    private String render(Model model, Page<AuditEvent> events, String baseUri, boolean showEmployer) {
        List<ActivityRow> rows = events.getContent().stream().map(views::activityRow).toList();
        model.addAttribute("events", new PageView<>(rows, events.getNumber(), events.getSize(),
                events.getTotalElements(), baseUri));
        model.addAttribute("showEmployer", showEmployer);
        return "audit/list";
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
