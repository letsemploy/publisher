package org.letsemploy.ojobpub_publisher.token;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
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
 * The API tokens screen (spec 7.17). Owners only - holding the list is close to
 * holding the access - which {@link ServiceTokenService} enforces, so no route
 * here can forget it.
 *
 * <p>Two ways in, as with People (spec 7.18): {@code /tokens} is the sidebar
 * destination and covers the active employer, {@code /employers/{id}/tokens}
 * names one explicitly. One handler behind both, and every write stays under the
 * employer path.
 */
@Controller
@RequiredArgsConstructor
public class ServiceTokenController {

    private final ServiceTokenService tokenService;
    private final EmployerService employerService;
    private final MembershipService membershipService;
    private final Scope scope;
    private final Views views;
    private final MessageSource messages;

    /** The sidebar destination; with no employer chosen there is nothing to show. */
    @GetMapping("/tokens")
    public String activeEmployerTokens(Model model) {
        java.util.Optional<org.letsemploy.ojobpub_publisher.employer.Employer> active =
                scope.activeEmployer();
        if (active.isEmpty()) {
            model.addAttribute("page", PageMeta.of(message("token.title")));
            model.addAttribute("tokens", null);
            return "token/list";
        }
        return list(active.get().getId(), model);
    }

    @GetMapping("/employers/{employerId}/tokens")
    public String list(@PathVariable UUID employerId, Model model) {
        populate(employerId, model, Map.of(), null);
        return "token/list";
    }

    private void populate(UUID employerId, Model model, Map<String, String> fieldErrors,
                          String name) {
        Employer employer = employerService.findVisible(employerId, scope.user());
        java.time.Instant now = java.time.Instant.now();
        List<TokenRow> tokens = tokenService.forEmployer(employerId, scope.user()).stream()
                .map(t -> views.tokenRow(t, now, tokenService.getExpiryWarningDays())).toList();

        model.addAttribute("page", new PageMeta(message("token.title"), employer.getName(),
                List.of(new Crumb(message("nav.employers"), "/employers"),
                        new Crumb(employer.getName(), "/employers/" + employerId),
                        new Crumb(message("token.title"), null))));
        model.addAttribute("employerId", employerId.toString());
        model.addAttribute("employerName", employer.getName());
        model.addAttribute("tokens", tokens);
        model.addAttribute("scopes", List.of(TokenScope.values()));
        model.addAttribute("name", name);
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("errors", fieldErrors.entrySet().stream()
                .map(e -> new FieldError(e.getKey(), e.getValue())).toList());
    }

    /**
     * The secret travels back as a flash attribute and is rendered once. It is
     * never put in the URL, never stored and never logged (spec 2.8) - a reload
     * of the list will not show it again.
     */
    @PostMapping("/employers/{employerId}/tokens")
    public String create(@PathVariable UUID employerId,
                         @RequestParam String name,
                         @RequestParam(defaultValue = "EDITOR") String role,
                         @RequestParam(required = false) List<String> scopes,
                         Model model, RedirectAttributes flash) {
        try {
            ServiceTokenService.CreatedToken created = tokenService.create(
                    employerId, name, MembershipRole.valueOf(role.toUpperCase()),
                    parseScopes(scopes), scope.user());
            flash.addFlashAttribute("createdSecret", created.getSecret());
            flash.addFlashAttribute("createdName", created.getToken().getName());
            return redirectToTokens(employerId);
        } catch (ValidationFailure e) {
            populate(employerId, model, e.getFieldErrors(), name);
            return "token/list";
        }
    }

    private Set<TokenScope> parseScopes(List<String> submitted) {
        Set<TokenScope> scopes = new LinkedHashSet<>();
        if (submitted != null) {
            for (String raw : submitted) {
                try {
                    scopes.add(TokenScope.valueOf(raw.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new ValidationFailure("scopes", "Unknown scope: " + raw);
                }
            }
        }
        return scopes;
    }

    @GetMapping("/employers/{employerId}/tokens/{tokenId}/revoke")
    public String revokeConfirm(@PathVariable UUID employerId, @PathVariable UUID tokenId,
                                Model model) {
        membershipService.requireOwner(scope.user(), employerId);
        TokenRow token = tokenService.forEmployer(employerId, scope.user()).stream()
                .map(t -> views.tokenRow(t, java.time.Instant.now(),
                        tokenService.getExpiryWarningDays()))
                .filter(t -> t.getId().equals(tokenId.toString()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Token not found: " + tokenId));
        model.addAttribute("token", token);
        model.addAttribute("employerId", employerId.toString());
        // Names the token and says what stops working, rather than asking "are you sure?".
        model.addAttribute("message", messages.getMessage("token.revoke.body",
                new Object[]{token.getName(), token.getPrefix()},
                LocaleContextHolder.getLocale()));
        return "token/revoke";
    }

    @PostMapping("/employers/{employerId}/tokens/{tokenId}/revoke")
    public String revoke(@PathVariable UUID employerId, @PathVariable UUID tokenId,
                         RedirectAttributes flash) {
        tokenService.revoke(tokenId, scope.user());
        flash.addFlashAttribute("successMsg", "token.revoked");
        return redirectToTokens(employerId);
    }

    /**
     * Renew, which is also Reactivate: the same date change either way (spec 2.8).
     * No confirmation modal - it is additive and reversible, unlike revoking.
     */
    @PostMapping("/employers/{employerId}/tokens/{tokenId}/renew")
    public String renew(@PathVariable UUID employerId, @PathVariable UUID tokenId,
                        RedirectAttributes flash) {
        try {
            tokenService.renew(tokenId, scope.user());
            flash.addFlashAttribute("successMsg", "token.renewed");
        } catch (ValidationFailure e) {
            flash.addFlashAttribute("errorMsg", "token.renew.refused");
        }
        return redirectToTokens(employerId);
    }

    /** Back to whichever of the two routes the user is on (spec 7.17). */
    private String redirectToTokens(UUID employerId) {
        return scope.activeEmployer()
                .filter(e -> e.getId().equals(employerId))
                .map(e -> "redirect:/tokens")
                .orElse("redirect:/employers/" + employerId + "/tokens");
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
