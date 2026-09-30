package org.letsemploy.ojobpub_publisher.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The sign-in page (spec 7.19): where a signed-out visitor starts, where a failed
 * sign-in lands ({@code ?error}), and where signing out ends ({@code ?logout}).
 *
 * <p>Reached only through the OIDC chain, which permits it. With no identity
 * provider configured the closed chain refuses it like everything else - a Sign
 * in button that cannot work would be worse than the refusal.
 */
@Controller
public class LoginController {

    private final CurrentUserService currentUserService;
    private final LoginOptions loginOptions;
    private final boolean localAccounts;

    public LoginController(CurrentUserService currentUserService, LoginOptions loginOptions,
                           org.letsemploy.ojobpub_publisher.account.LocalAccountProperties localAccounts) {
        this.currentUserService = currentUserService;
        this.loginOptions = loginOptions;
        this.localAccounts = localAccounts.enabled();
    }

    @GetMapping("/login")
    public String login(HttpServletRequest request, Model model) {
        // Nothing to do here when signed in, and the login routes are inert in
        // development, where the bypass has already signed everyone in (spec 2.3).
        if (currentUserService.isDevMode() || !currentUserService.current().isAnonymous()) {
            return "redirect:/";
        }
        // Present or not; the parameters carry no value (/login?error).
        model.addAttribute("error", request.getParameterMap().containsKey("error"));
        model.addAttribute("loggedOut", request.getParameterMap().containsKey("logout"));
        // Only ever shown to the person just signed out for it (spec 2.11), so it
        // tells nobody else anything.
        model.addAttribute("suspended", request.getParameterMap().containsKey("suspended"));
        // Local accounts (spec 2.12): the form, and what the account screens came back with.
        model.addAttribute("localAccounts", localAccounts);
        model.addAttribute("verified", request.getParameterMap().containsKey("verified"));
        model.addAttribute("passwordReset", request.getParameterMap().containsKey("reset"));
        model.addAttribute("expired", request.getParameterMap().containsKey("expired"));
        // One button per configured provider, sorted by name (spec 7.19).
        model.addAttribute("options", loginOptions.all());
        return "login";
    }
}
