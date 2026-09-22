package org.letsemploy.ojobpub_publisher.employer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.invitation.InvitationService;
import org.letsemploy.ojobpub_publisher.membership.Membership;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.web.Scope;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.letsemploy.ojobpub_publisher.web.view.*;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The employer People screen (spec 7.13): members, pending invitations and the
 * invite form. Admin only — enforced in the service, like every other rule here.
 */
@Controller
@RequestMapping("/employers/{employerId}/people")
@RequiredArgsConstructor
public class EmployerPeopleController {

    private final EmployerService employerService;
    private final InvitationService invitationService;
    private final MembershipService membershipService;
    private final Scope scope;
    private final Views views;
    private final MessageSource messages;

    @GetMapping
    public String people(@PathVariable UUID employerId, Model model) {
        // Owners of this employer and platform admins (spec 2.1); anyone else 404s.
        membershipService.requireOwner(scope.user(), employerId);
        populate(employerId, model, Map.of(), null);
        return "employer/people";
    }

    private void populate(UUID employerId, Model model,
                          Map<String, String> fieldErrors, String email) {
        Employer employer = employerService.findVisible(employerId, scope.user());
        List<MemberRow> members = membershipService.membersOf(employerId).stream()
                .map(m -> views.memberRow(m, membershipService.isLastOwner(employerId, m)))
                .toList();
        List<PendingInvitationRow> pending = invitationService.pendingForEmployer(employerId).stream()
                .map(views::pendingInvitationRow).toList();

        model.addAttribute("page", new PageMeta(message("employer.people.title"), employer.getName(),
                List.of(new Crumb(message("nav.employers"), "/employers"),
                        new Crumb(employer.getName(), "/employers/" + employerId),
                        new Crumb(message("employer.people.title"), null))));
        model.addAttribute("people",
                new PeopleView(employerId.toString(), employer.getName(), members, pending));
        model.addAttribute("email", email);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("errors", fieldErrors.entrySet().stream()
                .map(e -> new FieldError(e.getKey(), e.getValue())).toList());
    }

    @PostMapping("/invite")
    public String invite(@PathVariable UUID employerId, @RequestParam String email,
                         @RequestParam(defaultValue = "EDITOR") String role,
                         Model model, RedirectAttributes flash) {
        try {
            InvitationService.InviteOutcome outcome = invitationService.invite(
                    employerId, email, MembershipRole.valueOf(role.toUpperCase()), scope.user());
            // SENT is reported identically whether or not an account matched (spec 2.6).
            flash.addFlashAttribute("successMsg", switch (outcome) {
                case SENT -> "invitation.sent";
                case ALREADY_MEMBER -> "invitation.alreadyMember";
                case ALREADY_INVITED -> "invitation.alreadyInvited";
            });
            return "redirect:/employers/" + employerId + "/people";
        } catch (ValidationFailure e) {
            populate(employerId, model, e.getFieldErrors(), email);
            return "employer/people";
        }
    }

    @PostMapping("/invitations/{invitationId}/revoke")
    public String revoke(@PathVariable UUID employerId, @PathVariable UUID invitationId,
                         RedirectAttributes flash) {
        invitationService.revoke(invitationId, scope.user());
        flash.addFlashAttribute("successMsg", "invitation.revoked");
        return "redirect:/employers/" + employerId + "/people";
    }

    @GetMapping("/members/{userId}/remove")
    public String removeConfirm(@PathVariable UUID employerId, @PathVariable UUID userId,
                                Model model) {
        membershipService.requireOwner(scope.user(), employerId);
        Employer employer = employerService.findVisible(employerId, scope.user());
        MemberRow member = membershipService.membersOf(employerId).stream()
                .map(m -> views.memberRow(m, membershipService.isLastOwner(employerId, m)))
                .filter(m -> m.getId().equals(userId.toString()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Member not found: " + userId));
        model.addAttribute("member", member);
        model.addAttribute("employerId", employerId.toString());
        model.addAttribute("message", messages.getMessage("employer.people.remove.body",
                new Object[]{member.getDisplayName(), employer.getName()},
                LocaleContextHolder.getLocale()));
        return "employer/member_remove";
    }

    @PostMapping("/members/{userId}/remove")
    public String remove(@PathVariable UUID employerId, @PathVariable UUID userId,
                         RedirectAttributes flash) {
        try {
            membershipService.remove(employerId, userId, scope.user());
            flash.addFlashAttribute("successMsg", "employer.people.removed");
        } catch (ValidationFailure e) {
            // The last owner cannot be removed (spec 2.7); say what to do about it.
            flash.addFlashAttribute("errorMsg", "employer.people.lastOwner");
        }
        return "redirect:/employers/" + employerId + "/people";
    }

    @PostMapping("/members/{userId}/role")
    public String changeRole(@PathVariable UUID employerId, @PathVariable UUID userId,
                             @RequestParam String role, RedirectAttributes flash) {
        try {
            membershipService.changeRole(employerId, userId,
                    MembershipRole.valueOf(role.toUpperCase()), scope.user());
            flash.addFlashAttribute("successMsg", "employer.people.roleChanged");
        } catch (ValidationFailure e) {
            flash.addFlashAttribute("errorMsg", "employer.people.lastOwner");
        }
        return "redirect:/employers/" + employerId + "/people";
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
