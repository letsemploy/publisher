package org.letsemploy.ojobpub_publisher.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.security.AppUser;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.letsemploy.ojobpub_publisher.web.view.NavItem;
import org.letsemploy.ojobpub_publisher.web.view.Ref;
import org.letsemploy.ojobpub_publisher.web.view.UiContext;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Supplies the shell context to every back-office screen (spec 7.3). */
@ControllerAdvice(basePackages = "org.letsemploy.ojobpub_publisher")
@RequiredArgsConstructor
public class UiContextAdvice {

    private final CurrentUserService currentUserService;
    private final EmployerService employerService;
    private final EmployerContext employerContext;

    @ModelAttribute("ui")
    public UiContext ui(HttpServletRequest request) {
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
                new NavItem("tag", "nav.tags", "/tags", path.startsWith("/tags")));

        return new UiContext(user.getDisplayName(), user.isAdmin(),
                currentUserService.isDevMode(), employerContext.getTheme(),
                active, employers.stream().map(e -> new Ref(e.getId().toString(), e.getName())).toList(),
                nav, List.of("en", "de"));
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
