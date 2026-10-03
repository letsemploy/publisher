package org.letsemploy.ojobpub_publisher.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.letsemploy.ojobpub_publisher.web.view.PageMeta;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Deleting one's own account (spec 2.13, 7.25): from the user menu, for every
 * person signed in as themselves. The rules are {@link AccountDeletionService}'s.
 *
 * <p>Not while viewing as someone: the screen answers 404, and the POST never
 * gets here, because {@code ImpersonationGuard} refuses it first (spec 2.9).
 */
@Controller
public class AccountDeletionController {

    /** Where a deleted account lands: the sign-in page, saying it is done. */
    static final String DELETED = "/login?deleted";

    private final AccountDeletionService service;
    private final CurrentUserService currentUserService;
    private final MessageSource messages;

    public AccountDeletionController(AccountDeletionService service,
                                     CurrentUserService currentUserService,
                                     MessageSource messages) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.messages = messages;
    }

    @GetMapping("/account/delete")
    public String confirm(Model model) {
        return page(self(), false, model);
    }

    @PostMapping("/account/delete")
    public String delete(@RequestParam(required = false) String confirmEmail,
                         HttpServletRequest request, HttpServletResponse response, Model model) {
        Actor actor = self();
        try {
            service.delete(actor, confirmEmail);
        } catch (ValidationFailure e) {
            return page(actor, true, model);
        }
        // There is nobody left to be signed in as: end the session, and with it
        // any admin mode, viewing-as or active employer it held.
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
        return "redirect:" + DELETED;
    }

    private String page(Actor actor, boolean mismatch, Model model) {
        model.addAttribute("page", PageMeta.of(messages.getMessage("account.delete.title", null,
                LocaleContextHolder.getLocale())));
        model.addAttribute("deletion", service.preview(actor));
        model.addAttribute("mismatch", mismatch);
        return "user/delete-account";
    }

    private Actor self() {
        if (currentUserService.isImpersonating()) {
            throw new NotFoundException("Not found.");
        }
        return currentUserService.current();
    }
}
