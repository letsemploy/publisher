package org.letsemploy.ojobpub_publisher.invitation;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.web.EmployerContext;
import org.letsemploy.ojobpub_publisher.web.Scope;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.letsemploy.ojobpub_publisher.web.view.InvitationRow;
import org.letsemploy.ojobpub_publisher.web.view.PageMeta;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The invitee's own list (spec 7.16). The one screen that must work for a user
 * with no employer memberships at all.
 */
@Controller
@RequestMapping("/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;
    private final EmployerContext employerContext;
    private final Scope scope;
    private final Views views;
    private final MessageSource messages;

    @GetMapping
    public String list(Model model) {
        List<InvitationRow> rows = invitationService.pendingFor(scope.user()).stream()
                .map(views::invitationRow)
                .toList();
        model.addAttribute("page", PageMeta.of(message("nav.invitations")));
        model.addAttribute("invitations", rows);
        return "invitation/list";
    }

    /**
     * Accepting creates the membership, then puts the user inside the employer they
     * just joined rather than back on a list (spec 7.16).
     */
    @PostMapping("/{id}/accept")
    public String accept(@PathVariable UUID id, RedirectAttributes flash) {
        Employer employer = invitationService.accept(id, scope.user());
        employerContext.setActiveEmployerId(employer.getId());
        flash.addFlashAttribute("successMsg", "invitation.accepted");
        return "redirect:/";
    }

    @PostMapping("/{id}/decline")
    public String decline(@PathVariable UUID id, RedirectAttributes flash) {
        invitationService.decline(id, scope.user());
        flash.addFlashAttribute("successMsg", "invitation.declined");
        return "redirect:/invitations";
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
