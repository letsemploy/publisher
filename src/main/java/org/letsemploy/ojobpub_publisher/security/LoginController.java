package org.letsemploy.ojobpub_publisher.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class LoginController {

    private final CurrentUserService currentUserService;

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
        return "login";
    }
}
