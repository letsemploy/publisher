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
    /** The address of the last failed sign-in, shown once more so it need not be typed again. */
    static final String LAST_EMAIL = LoginController.class.getName() + ".lastEmail";

    private final boolean localAccounts;
    private final org.letsemploy.ojobpub_publisher.account.LoginAttempts attempts;
    private final org.letsemploy.ojobpub_publisher.account.CaptchaCheck captcha;

    public LoginController(CurrentUserService currentUserService, LoginOptions loginOptions,
                           org.letsemploy.ojobpub_publisher.account.LocalAccountProperties localAccounts,
                           org.letsemploy.ojobpub_publisher.account.LoginAttempts attempts,
                           org.letsemploy.ojobpub_publisher.account.CaptchaCheck captcha) {
        this.currentUserService = currentUserService;
        this.loginOptions = loginOptions;
        this.localAccounts = localAccounts.enabled();
        this.attempts = attempts;
        this.captcha = captcha;
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
        // A deleted account (spec 2.13) is signed out too, and has nothing left to
        // sign in to: the page offers the way back, not the form.
        boolean deleted = request.getParameterMap().containsKey("deleted");
        model.addAttribute("accountDeleted", deleted);
        model.addAttribute("loggedOut", deleted || request.getParameterMap().containsKey("logout"));
        // Only ever shown to the person just signed out for it (spec 2.11), so it
        // tells nobody else anything.
        model.addAttribute("suspended", request.getParameterMap().containsKey("suspended"));
        // Local accounts (spec 2.12): the form, and what the account screens came back with.
        model.addAttribute("localAccounts", localAccounts);
        model.addAttribute("verified", request.getParameterMap().containsKey("verified"));
        model.addAttribute("passwordReset", request.getParameterMap().containsKey("reset"));
        model.addAttribute("expired", request.getParameterMap().containsKey("expired"));
        // Only what this visitor typed a moment ago, and only once (spec 7.7).
        var session = request.getSession(false);
        if (session != null) {
            model.addAttribute("email", session.getAttribute(LAST_EMAIL));
            session.removeAttribute(LAST_EMAIL);
        }
        // After repeated failures from here, or when a sign-in was just refused for
        // want of one, the form carries the captcha (spec 2.12).
        boolean captchaMissing = request.getParameterMap().containsKey("captcha");
        model.addAttribute("captchaMissing", localAccounts && captchaMissing);
        model.addAttribute("loginCaptcha", localAccounts && captcha.configured()
                && (captchaMissing || attempts.captchaRequired(request.getRemoteAddr())));
        // One button per configured provider, sorted by name (spec 7.19).
        model.addAttribute("options", loginOptions.all());
        return "login";
    }
}
