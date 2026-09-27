package org.letsemploy.ojobpub_publisher.user;

import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.web.Scope;
import org.letsemploy.ojobpub_publisher.web.view.PageMeta;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The Users screen (spec 7.20): admin only, enforced in {@link UserService}. */
@Controller
@RequiredArgsConstructor
public class UsersController {

    private final UserService userService;
    private final Scope scope;
    private final MessageSource messages;

    @GetMapping("/users")
    public String list(Model model, @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page) {
        model.addAttribute("users", userService.page(q, page, scope.user()));
        model.addAttribute("query", q);
        model.addAttribute("page", PageMeta.of(messages.getMessage("nav.users", null, "nav.users",
                LocaleContextHolder.getLocale())));
        return "user/list";
    }
}
