package org.letsemploy.ojobpub_publisher.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.letsemploy.ojobpub_publisher.web.view.FieldError;
import org.letsemploy.ojobpub_publisher.web.view.PageMeta;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The account screens (spec 7.24): signing up, completing a sign-up from its
 * link, resending that link, a forgotten password, and changing one's own.
 *
 * <p>Only present with {@code app.local-accounts.enabled}; otherwise every route
 * here is a 404. The forms that send mail are throttled per client, which says
 * nothing about any address (spec 2.12).
 */
@Controller
@ConditionalOnProperty(prefix = "app.local-accounts", name = "enabled", havingValue = "true")
public class AccountController {

    private final LocalAccountService service;
    private final CurrentUserService currentUserService;
    private final MessageSource messages;
    private final Throttle mailsPerClient;
    private final int minPasswordLength;

    public AccountController(LocalAccountService service, CurrentUserService currentUserService,
                             MessageSource messages, LocalAccountProperties properties) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.messages = messages;
        this.mailsPerClient = new Throttle(properties.maxMails(), properties.throttleWindow(), Clock.systemUTC());
        this.minPasswordLength = properties.minPasswordLength();
    }

    /** The minimum the password hint states, before the rule is broken (spec 7.24). */
    @ModelAttribute("minPasswordLength")
    int minPasswordLength() {
        return minPasswordLength;
    }

    /** The language switch keeps every other parameter - a link's token above all. */
    @ModelAttribute("languageLinks")
    Map<String, String> languageLinks() {
        Map<String, String> links = new java.util.LinkedHashMap<>();
        for (String language : List.of("en", "de")) {
            links.put(language, ServletUriComponentsBuilder.fromCurrentRequest()
                    .replaceQueryParam("lang", language).build().toUriString());
        }
        return links;
    }

    // ------------------------------------------------------------------ sign-up

    @GetMapping("/register")
    public String register(Model model) {
        if (signedIn()) {
            return "redirect:/";
        }
        model.addAttribute("form", new AccountForms.SignUp(null, null));
        return "account/register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("form") AccountForms.SignUp form, HttpServletRequest request,
                           Model model) {
        if (throttled(request, model)) {
            return "account/register";
        }
        try {
            service.signUp(form, locale());
        } catch (ValidationFailure e) {
            errors(model, e);
            return "account/register";
        }
        return "redirect:/register/sent";
    }

    /** One page, whatever happened (spec 2.12). */
    @GetMapping({"/register/sent", "/password/sent"})
    public String sent(HttpServletRequest request, Model model) {
        model.addAttribute("reset", request.getRequestURI().endsWith("/password/sent"));
        return "account/sent";
    }

    @GetMapping("/register/complete")
    public String complete(@RequestParam(required = false) String token, Model model) {
        return choosePage(token, AccountToken.Purpose.VERIFY_EMAIL, model);
    }

    @PostMapping("/register/complete")
    public String complete(@ModelAttribute("form") AccountForms.ChoosePassword form, Model model) {
        return choose(form, AccountToken.Purpose.VERIFY_EMAIL, "redirect:/login?verified", model);
    }

    @GetMapping("/register/resend")
    public String resend(Model model) {
        model.addAttribute("form", new AccountForms.Address(null));
        model.addAttribute("reset", false);
        return "account/address";
    }

    @PostMapping("/register/resend")
    public String resend(@ModelAttribute("form") AccountForms.Address form, HttpServletRequest request,
                         Model model) {
        model.addAttribute("reset", false);
        if (throttled(request, model)) {
            return "account/address";
        }
        try {
            service.resend(form, locale());
        } catch (ValidationFailure e) {
            errors(model, e);
            return "account/address";
        }
        return "redirect:/register/sent";
    }

    // ---------------------------------------------------------- forgot / reset

    @GetMapping("/password/forgot")
    public String forgot(Model model) {
        model.addAttribute("form", new AccountForms.Address(null));
        model.addAttribute("reset", true);
        return "account/address";
    }

    @PostMapping("/password/forgot")
    public String forgot(@ModelAttribute("form") AccountForms.Address form, HttpServletRequest request,
                         Model model) {
        model.addAttribute("reset", true);
        if (throttled(request, model)) {
            return "account/address";
        }
        try {
            service.forgot(form, locale());
        } catch (ValidationFailure e) {
            errors(model, e);
            return "account/address";
        }
        return "redirect:/password/sent";
    }

    @GetMapping("/password/reset")
    public String reset(@RequestParam(required = false) String token, Model model) {
        return choosePage(token, AccountToken.Purpose.RESET_PASSWORD, model);
    }

    @PostMapping("/password/reset")
    public String reset(@ModelAttribute("form") AccountForms.ChoosePassword form, Model model) {
        return choose(form, AccountToken.Purpose.RESET_PASSWORD, "redirect:/login?reset", model);
    }

    // ------------------------------------------------------------ change own

    @GetMapping("/account/password")
    public String password(Model model) {
        LocalUser user = localUser();
        model.addAttribute("page", PageMeta.of(message("account.password.title")));
        model.addAttribute("email", user.getEmail());
        return "account/password";
    }

    @PostMapping("/account/password")
    public String password(@ModelAttribute("form") AccountForms.ChangePassword form, HttpServletRequest request,
                           HttpServletResponse response, Model model, RedirectAttributes flash) {
        LocalUser user = localUser();
        Instant changed;
        try {
            changed = service.changePassword(user.getId(), form, currentUserService.current());
        } catch (ValidationFailure e) {
            model.addAttribute("page", PageMeta.of(message("account.password.title")));
            model.addAttribute("email", user.getEmail());
            errors(model, e);
            return "account/password";
        }
        // Every other session ends at its next request; this one carries on (spec 2.12).
        Authentication renewed = UsernamePasswordAuthenticationToken.authenticated(
                user.renewedAt(changed), null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(renewed);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        flash.addFlashAttribute("successMsg", "account.password.changed");
        return "redirect:/account/password";
    }

    // ------------------------------------------------------------------ helpers

    private String choosePage(String token, AccountToken.Purpose purpose, Model model) {
        if (service.usable(token, purpose).isEmpty()) {
            model.addAttribute("reset", purpose == AccountToken.Purpose.RESET_PASSWORD);
            return "account/expired";
        }
        model.addAttribute("form", new AccountForms.ChoosePassword(token, null, null, null));
        model.addAttribute("reset", purpose == AccountToken.Purpose.RESET_PASSWORD);
        return "account/choose";
    }

    private String choose(AccountForms.ChoosePassword form, AccountToken.Purpose purpose, String done,
                          Model model) {
        model.addAttribute("reset", purpose == AccountToken.Purpose.RESET_PASSWORD);
        try {
            if (!service.choosePassword(form, purpose)) {
                return "account/expired";
            }
        } catch (ValidationFailure e) {
            // Passwords are never sent back into the page.
            model.addAttribute("form", new AccountForms.ChoosePassword(form.token(), null, null, null));
            errors(model, e);
            return "account/choose";
        }
        return done;
    }

    private boolean throttled(HttpServletRequest request, Model model) {
        String client = request.getRemoteAddr();
        if (mailsPerClient.exhausted(client)) {
            model.addAttribute("errors", List.of(new FieldError("account.throttled", message("account.throttled"))));
            model.addAttribute("fieldErrors", Map.of());
            return true;
        }
        mailsPerClient.record(client);
        return false;
    }

    private void errors(Model model, ValidationFailure e) {
        model.addAttribute("fieldErrors", e.getFieldErrors());
        model.addAttribute("errors", e.getFieldErrors().entrySet().stream()
                .map(entry -> new FieldError(entry.getKey(), entry.getValue())).toList());
    }

    /** The account signed in, if it is a local one; any other person has no password here. */
    private LocalUser localUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LocalUser user
                && !currentUserService.isImpersonating()) {
            return user;
        }
        throw new NotFoundException("Not a local account");
    }

    private boolean signedIn() {
        return !currentUserService.current().isAnonymous();
    }

    private Locale locale() {
        return LocaleContextHolder.getLocale();
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, locale());
    }
}
