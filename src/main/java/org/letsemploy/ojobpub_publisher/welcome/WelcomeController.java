package org.letsemploy.ojobpub_publisher.welcome;

import java.util.Locale;
import java.util.Optional;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.manual.ManualTopic;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.letsemploy.ojobpub_publisher.web.Scope;
import org.letsemploy.ojobpub_publisher.web.view.PageMeta;
import org.letsemploy.ojobpub_publisher.web.view.WelcomeView;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The Welcome page (spec 7.30): an introduction in five steps, shown after the
 * sign-in that created the account and from the user menu after that. It reads
 * nothing but who the person is and changes nothing, so it is the same while
 * viewing as someone (spec 2.9).
 */
@Controller
public class WelcomeController {

    private final Scope scope;
    private final MembershipService membershipService;
    private final CurrentUserService currentUserService;
    private final MessageSource messages;

    public WelcomeController(Scope scope, MembershipService membershipService,
                             CurrentUserService currentUserService, MessageSource messages) {
        this.scope = scope;
        this.membershipService = membershipService;
        this.currentUserService = currentUserService;
        this.messages = messages;
    }

    @GetMapping("/welcome")
    public String welcome(Model model) {
        Actor actor = scope.user();
        Optional<Employer> active = scope.activeEmployer();
        // API tokens are an owner's (spec 7.17): an editor is told about them, not sent there.
        boolean administers = active.map(e -> membershipService.canAdminister(actor, e.getId())).orElse(false);
        Locale locale = LocaleContextHolder.getLocale();
        model.addAttribute("page", PageMeta.of(message("welcome.title")).help(ManualTopic.WELCOME));
        model.addAttribute("welcome", new WelcomeView(active.isPresent(), administers,
                !currentUserService.isImpersonating(),
                ManualTopic.EMPLOYERS.path(locale), ManualTopic.JOB.path(locale),
                ManualTopic.PERMALINKS.path(locale), ManualTopic.PEOPLE.path(locale)));
        return "welcome/welcome";
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
