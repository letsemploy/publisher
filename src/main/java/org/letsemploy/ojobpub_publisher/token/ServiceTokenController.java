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
 */
@Controller
@RequestMapping("/employers/{employerId}/tokens")
@RequiredArgsConstructor
public class ServiceTokenController {

    private final ServiceTokenService tokenService;
    private final EmployerService employerService;
    private final MembershipService membershipService;
    private final Scope scope;
    private final Views views;
    private final MessageSource messages;

    @GetMapping
    public String list(@PathVariable UUID employerId, Model model) {
        populate(employerId, model, Map.of(), null);
        return "token/list";
    }

    private void populate(UUID employerId, Model model, Map<String, String> fieldErrors,
                          String name) {
        Employer employer = employerService.findVisible(employerId, scope.user());
        List<TokenRow> tokens = tokenService.forEmployer(employerId, scope.user()).stream()
                .map(views::tokenRow).toList();

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
    @PostMapping
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
            return "redirect:/employers/" + employerId + "/tokens";
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

    @GetMapping("/{tokenId}/revoke")
    public String revokeConfirm(@PathVariable UUID employerId, @PathVariable UUID tokenId,
                                Model model) {
        membershipService.requireOwner(scope.user(), employerId);
        TokenRow token = tokenService.forEmployer(employerId, scope.user()).stream()
                .map(views::tokenRow)
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

    @PostMapping("/{tokenId}/revoke")
    public String revoke(@PathVariable UUID employerId, @PathVariable UUID tokenId,
                         RedirectAttributes flash) {
        tokenService.revoke(tokenId, scope.user());
        flash.addFlashAttribute("successMsg", "token.revoked");
        return "redirect:/employers/" + employerId + "/tokens";
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
