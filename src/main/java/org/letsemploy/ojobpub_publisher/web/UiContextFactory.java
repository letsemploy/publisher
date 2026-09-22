package org.letsemploy.ojobpub_publisher.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.invitation.InvitationService;
import org.letsemploy.ojobpub_publisher.security.AppUser;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.letsemploy.ojobpub_publisher.web.view.NavItem;
import org.letsemploy.ojobpub_publisher.web.view.Ref;
import org.letsemploy.ojobpub_publisher.web.view.UiContext;
import org.springframework.stereotype.Component;

/**
 * Builds the shell context (spec 7.3).
 *
 * <p>A component rather than logic inside the advice, because the error page needs
 * it too: Spring does not apply {@code @ModelAttribute} contributions to
 * {@code @ExceptionHandler} methods, so an error view that expected the advice to
 * have supplied {@code ui} would fail to render — turning every 404 into a 500.
 */
@Component
@RequiredArgsConstructor
public class UiContextFactory {

    private final CurrentUserService currentUserService;
    private final EmployerService employerService;
    private final EmployerContext employerContext;
    private final InvitationService invitationService;

    public UiContext build(HttpServletRequest request) {
        AppUser user = currentUserService.current();
        List<Employer> employers = employerService.visibleTo(user);
        Ref active = activeEmployer(employers);

        String path = request.getRequestURI();
        List<NavItem> nav = List.of(
                new NavItem("layout-dashboard", "nav.dashboard", "/", path.equals("/")),
                new NavItem("briefcase", "nav.jobs", "/jobs", path.startsWith("/jobs")),
                new NavItem("rss", "nav.feeds", "/feeds", path.startsWith("/feeds")),
                new NavItem("building", "nav.employers", "/employers", path.startsWith("/employers")),
                new NavItem("map-pin", "nav.locations", "/locations", path.startsWith("/locations")),
                new NavItem("tag", "nav.tags", "/tags", path.startsWith("/tags")),
                // Stays visible when empty: a user with no memberships has nothing
                // else to do, and an entry that vanishes cannot be checked (spec 7.3).
                new NavItem("mail", "nav.invitations", "/invitations",
                        path.startsWith("/invitations")));

        return new UiContext(user.getDisplayName(), user.isAdmin(),
                currentUserService.isDevMode(), employerContext.getTheme(),
                active, employers.stream().map(e -> new Ref(e.getId().toString(), e.getName())).toList(),
                nav, List.of("en", "de"), invitationService.countPendingFor(user));
    }

    /**
     * With exactly one employer the switcher is hidden and that employer is
     * selected automatically (spec 2.5).
     */
    private Ref activeEmployer(List<Employer> employers) {
        UUID activeId = employerContext.getActiveEmployerId();
        if (activeId == null && employers.size() == 1) {
            activeId = employers.get(0).getId();
            employerContext.setActiveEmployerId(activeId);
        }
        if (activeId == null) {
            return null;
        }
        UUID target = activeId;
        return employers.stream()
                .filter(e -> e.getId().equals(target))
                .findFirst()
                .map(e -> new Ref(e.getId().toString(), e.getName()))
                .orElse(null);
    }
}
