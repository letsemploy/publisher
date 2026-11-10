package org.letsemploy.ojobpub_publisher.token;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.manual.ManualTopic;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.web.FormViews;
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
public class ServiceTokenController {

    private final ServiceTokenService tokenService;
    private final EmployerService employerService;
    private final MembershipService membershipService;
    private final Scope scope;
    private final Views views;
    private final MessageSource messages;

    public ServiceTokenController(ServiceTokenService tokenService,
                                  EmployerService employerService,
                                  MembershipService membershipService,
                                  Scope scope,
                                  Views views,
                                  MessageSource messages) {
        this.tokenService = tokenService;
        this.employerService = employerService;
        this.membershipService = membershipService;
        this.scope = scope;
        this.views = views;
        this.messages = messages;
    }

    /** The sidebar destination; with no employer chosen there is nothing to show. */
    @GetMapping("/tokens")
    public String activeEmployerTokens(Model model) {
        java.util.Optional<org.letsemploy.ojobpub_publisher.employer.Employer> active =
                scope.activeEmployer();
        if (active.isEmpty()) {
            model.addAttribute("page", PageMeta.of(message("token.title")).help(ManualTopic.TOKENS));
            model.addAttribute("tokens", null);
            return "token/list";
        }
        return list(active.get().getId(), model);
    }

    @GetMapping("/employers/{employerId}/tokens")
    public String list(@PathVariable UUID employerId, Model model) {
        populate(employerId, model);
        return "token/list";
    }

    private void populate(UUID employerId, Model model) {
        Employer employer = employerService.findVisible(employerId, scope.user());
        java.time.Instant now = java.time.Instant.now();
        List<TokenRow> tokens = tokenService.forEmployer(employerId, scope.user()).stream()
                .map(t -> views.tokenRow(t, now, tokenService.getExpiryWarningDays())).toList();

        model.addAttribute("page", new PageMeta(message("token.title"), employer.getName(),
                List.of(new Crumb(message("nav.employers"), "/employers"),
                        new Crumb(employer.getName(), "/employers/" + employerId),
                        new Crumb(message("token.title"), null))).help(ManualTopic.TOKENS));
        model.addAttribute("employerId", employerId.toString());
        model.addAttribute("employerName", employer.getName());
        model.addAttribute("tokens", tokens);
    }

    /**
     * The create form (spec 7.17), a modal from the list's button and a page by
     * its URL (spec 7.4). Owners only, like the token list: anyone else gets the
     * 404 every refusal is.
     */
    @GetMapping("/employers/{employerId}/tokens/create")
    public String createForm(@PathVariable UUID employerId, Model model) {
        formModel(employerId, model, Map.of(), null, MembershipRole.EDITOR.name(), Set.of());
        return FormViews.view("token/form");
    }

    private void formModel(UUID employerId, Model model, Map<String, String> fieldErrors,
                           String name, String role, Set<String> chosenScopes) {
        membershipService.requireOwner(scope.user(), employerId);
        Employer employer = employerService.findVisible(employerId, scope.user());
        model.addAttribute("page", new PageMeta(message("token.create"), employer.getName(),
                List.of(new Crumb(message("token.title"), tokensPath(employerId)),
                        new Crumb(message("token.create"), null))).help(ManualTopic.TOKENS));
        model.addAttribute("employerId", employerId.toString());
        model.addAttribute("scopes", List.of(TokenScope.values()));
        // Every value comes back on a refusal (spec 7.7), the role and scopes too.
        model.addAttribute("name", name);
        model.addAttribute("role", role);
        model.addAttribute("chosenScopes", chosenScopes);
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
            flash.addFlashAttribute("createdSecret", created.secret());
            flash.addFlashAttribute("createdName", created.token().getName());
            return FormViews.redirect(tokensPath(employerId));
        } catch (ValidationFailure e) {
            Set<String> chosen = scopes == null ? Set.of() : Set.copyOf(scopes.stream()
                    .map(String::toUpperCase).toList());
            formModel(employerId, model, e.getFieldErrors(), name, role.toUpperCase(), chosen);
            return FormViews.view("token/form");
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
                .filter(t -> t.id().equals(tokenId.toString()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Token not found: " + tokenId));
        model.addAttribute("token", token);
        model.addAttribute("employerId", employerId.toString());
        // Names the token and says what stops working, rather than asking "are you sure?".
        model.addAttribute("message", messages.getMessage("token.revoke.body",
                new Object[]{token.name(), token.prefix()},
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
        return "redirect:" + tokensPath(employerId);
    }

    private String tokensPath(UUID employerId) {
        return scope.activeEmployer()
                .filter(e -> e.getId().equals(employerId))
                .map(e -> "/tokens")
                .orElse("/employers/" + employerId + "/tokens");
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
