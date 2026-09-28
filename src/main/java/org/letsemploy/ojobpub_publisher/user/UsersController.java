package org.letsemploy.ojobpub_publisher.user;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.web.Scope;
import org.letsemploy.ojobpub_publisher.web.view.PageMeta;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The Users screen (spec 7.20), and suspending and reinstating an account from it
 * (spec 2.11): admin only, enforced in {@link UserService}.
 */
@Controller
@RequiredArgsConstructor
public class UsersController {

    private final UserService userService;
    private final Scope scope;
    private final MessageSource messages;

    @GetMapping("/users")
    public String list(Model model, @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "false") boolean suspended,
                       @RequestParam(defaultValue = "0") int page) {
        model.addAttribute("users", userService.page(q, suspended, page, scope.user()));
        model.addAttribute("query", q);
        model.addAttribute("suspendedOnly", suspended);
        model.addAttribute("page", PageMeta.of(message("nav.users")));
        return "user/list";
    }

    @GetMapping("/users/{id}/suspend")
    public String suspendConfirm(@PathVariable UUID id, Model model, RedirectAttributes flash) {
        try {
            return confirmation(model, id, null);
        } catch (ValidationFailure e) {
            return refused(e, flash);
        }
    }

    @PostMapping("/users/{id}/suspend")
    public String suspend(@PathVariable UUID id, @RequestParam(required = false) String reason,
                          Model model, RedirectAttributes flash) {
        try {
            userService.suspend(id, reason, scope.user());
        } catch (ValidationFailure e) {
            if (!e.getFieldErrors().containsKey("reason")) {
                return refused(e, flash);
            }
            // Back to the form with what was typed (spec 7.7).
            model.addAttribute("reasonError", message("users.suspend.reasonTooLong"));
            return confirmation(model, id, reason);
        }
        flash.addFlashAttribute("successMsg", "users.suspended");
        return "redirect:/users";
    }

    @PostMapping("/users/{id}/reinstate")
    public String reinstate(@PathVariable UUID id, RedirectAttributes flash) {
        userService.reinstate(id, scope.user());
        flash.addFlashAttribute("successMsg", "users.reinstated");
        return "redirect:/users";
    }

    private String confirmation(Model model, UUID id, String reason) {
        model.addAttribute("suspension", userService.suspension(id, scope.user()));
        model.addAttribute("reason", reason);
        model.addAttribute("page", PageMeta.of(message("users.suspend.title")));
        return "user/suspend";
    }

    /** Oneself or an admin (spec 2.11): said, on the screen the request came from. */
    private static String refused(ValidationFailure e, RedirectAttributes flash) {
        flash.addFlashAttribute("errorMsg", e.getFieldErrors().containsKey("self")
                ? "users.suspend.notSelf" : "users.suspend.notAdmin");
        return "redirect:/users";
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
