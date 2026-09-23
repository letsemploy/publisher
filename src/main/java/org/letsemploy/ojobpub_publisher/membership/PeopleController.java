package org.letsemploy.ojobpub_publisher.membership;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.invitation.InvitationService;
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
 * The People screen (spec 7.18): who belongs to an employer.
 *
 * <p>Two ways in. {@code /people} is the sidebar destination and covers the
 * active employer (spec 2.5); {@code /employers/{id}/people} names one
 * explicitly, for an admin or anyone with several. One handler behind both, so
 * the two cannot drift.
 *
 * <p>Reading takes membership and every write takes the owner role, both enforced
 * in the services. What the template does with {@code canAdminister} is
 * presentation, never the check (spec 2.4).
 */
@Controller
@RequiredArgsConstructor
public class PeopleController {

    private final EmployerService employerService;
    private final InvitationService invitationService;
    private final MembershipService membershipService;
    private final Scope scope;
    private final Views views;
    private final MessageSource messages;

    /**
     * The sidebar destination. With "All employers" chosen, or no memberships at
     * all, there is no subject to name, so the screen says so rather than picking
     * one (spec 7.18).
     */
    @GetMapping("/people")
    public String activeEmployerPeople(Model model) {
        Optional<Employer> active = scope.activeEmployer();
        if (active.isEmpty()) {
            model.addAttribute("page", PageMeta.of(message("nav.people")));
            model.addAttribute("people", null);
            return "membership/people";
        }
        return people(active.get().getId(), model);
    }

    @GetMapping("/employers/{employerId}/people")
    public String people(@PathVariable UUID employerId, Model model) {
        // Membership of this employer, or the platform admin role; anyone else
        // gets 404 from findVisible, never 403 (spec 2.4).
        populate(employerId, model, Map.of(), null);
        return "membership/people";
    }

    private void populate(UUID employerId, Model model,
                          Map<String, String> fieldErrors, String email) {
        Employer employer = employerService.findVisible(employerId, scope.user());
        boolean canAdminister = membershipService.canAdminister(scope.user(), employerId);
        List<MemberRow> members = memberRows(employerId);
        // Pending invitations are the owner's working material, so an editor is
        // not given them at all rather than shown an empty section (spec 7.18).
        List<PendingInvitationRow> pending = canAdminister
                ? invitationService.pendingForEmployer(employerId).stream()
                        .map(views::pendingInvitationRow).toList()
                : List.of();

        model.addAttribute("page", new PageMeta(message("employer.people.title"), employer.getName(),
                List.of(new Crumb(message("nav.employers"), "/employers"),
                        new Crumb(employer.getName(), "/employers/" + employerId),
                        new Crumb(message("employer.people.title"), null))));
        model.addAttribute("people", new PeopleView(employerId.toString(), employer.getName(),
                members, pending, canAdminister));
        model.addAttribute("email", email);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("errors", fieldErrors.entrySet().stream()
                .map(e -> new FieldError(e.getKey(), e.getValue())).toList());
    }

    @PostMapping("/employers/{employerId}/people/invite")
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
            return redirectToPeople(employerId);
        } catch (ValidationFailure e) {
            populate(employerId, model, e.getFieldErrors(), email);
            return "membership/people";
        }
    }

    @PostMapping("/employers/{employerId}/people/invitations/{invitationId}/revoke")
    public String revoke(@PathVariable UUID employerId, @PathVariable UUID invitationId,
                         RedirectAttributes flash) {
        invitationService.revoke(invitationId, scope.user());
        flash.addFlashAttribute("successMsg", "invitation.revoked");
        return redirectToPeople(employerId);
    }

    @GetMapping("/employers/{employerId}/people/members/{userId}/remove")
    public String removeConfirm(@PathVariable UUID employerId, @PathVariable UUID userId,
                                Model model) {
        membershipService.requireOwner(scope.user(), employerId);
        Employer employer = employerService.findVisible(employerId, scope.user());
        MemberRow member = memberRows(employerId).stream()
                .filter(m -> m.getId().equals(userId.toString()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Member not found: " + userId));
        model.addAttribute("member", member);
        model.addAttribute("employerId", employerId.toString());
        model.addAttribute("message", messages.getMessage("employer.people.remove.body",
                new Object[]{member.getDisplayName(), employer.getName()},
                LocaleContextHolder.getLocale()));
        return "membership/member_remove";
    }

    @PostMapping("/employers/{employerId}/people/members/{userId}/remove")
    public String remove(@PathVariable UUID employerId, @PathVariable UUID userId,
                         RedirectAttributes flash) {
        try {
            membershipService.remove(employerId, userId, scope.user());
            flash.addFlashAttribute("successMsg", "employer.people.removed");
        } catch (ValidationFailure e) {
            // The last owner cannot be removed (spec 2.7); say what to do about it.
            flash.addFlashAttribute("errorMsg", "employer.people.lastOwner");
        }
        return redirectToPeople(employerId);
    }

    @PostMapping("/employers/{employerId}/people/members/{userId}/role")
    public String changeRole(@PathVariable UUID employerId, @PathVariable UUID userId,
                             @RequestParam String role, RedirectAttributes flash) {
        try {
            membershipService.changeRole(employerId, userId,
                    MembershipRole.valueOf(role.toUpperCase()), scope.user());
            flash.addFlashAttribute("successMsg", "employer.people.roleChanged");
        } catch (ValidationFailure e) {
            flash.addFlashAttribute("errorMsg", "employer.people.lastOwner");
        }
        return redirectToPeople(employerId);
    }

    @PostMapping("/employers/{employerId}/people/members/{userId}/suspend")
    public String suspend(@PathVariable UUID employerId, @PathVariable UUID userId,
                          RedirectAttributes flash) {
        try {
            membershipService.suspend(employerId, userId, scope.user());
            flash.addFlashAttribute("successMsg", "employer.people.suspended");
        } catch (ValidationFailure e) {
            // Not oneself, and not the last active owner (spec 2.7).
            flash.addFlashAttribute("errorMsg", e.getFieldErrors().containsKey("self")
                    ? "employer.people.cannotSuspendSelf"
                    : "employer.people.lastOwner");
        }
        return redirectToPeople(employerId);
    }

    @PostMapping("/employers/{employerId}/people/members/{userId}/reinstate")
    public String reinstate(@PathVariable UUID employerId, @PathVariable UUID userId,
                            RedirectAttributes flash) {
        membershipService.reinstate(employerId, userId, scope.user());
        flash.addFlashAttribute("successMsg", "employer.people.reinstated");
        return redirectToPeople(employerId);
    }

    private List<MemberRow> memberRows(UUID employerId) {
        UUID viewer = scope.user().getId();
        return membershipService.membersOf(employerId).stream()
                .map(m -> views.memberRow(m, membershipService.isLastOwner(employerId, m), viewer))
                .toList();
    }

    /**
     * Back to whichever of the two routes the user is on: the sidebar screen when
     * this is the active employer, the employer's own otherwise. Redirecting
     * always to one of them would move people off the screen they were using.
     */
    private String redirectToPeople(UUID employerId) {
        return scope.activeEmployer()
                .filter(e -> e.getId().equals(employerId))
                .map(e -> "redirect:/people")
                .orElse("redirect:/employers/" + employerId + "/people");
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
