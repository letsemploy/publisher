package org.letsemploy.ojobpub_publisher.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.config.WebLangConfig;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.invitation.InvitationService;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.user.PictureService;
import org.letsemploy.ojobpub_publisher.web.view.Avatar;
import org.letsemploy.ojobpub_publisher.web.view.NavItem;
import org.letsemploy.ojobpub_publisher.web.view.Ref;
import org.letsemploy.ojobpub_publisher.web.view.UiContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
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
public class UiContextFactory {

    private final CurrentUserService currentUserService;
    private final Scope scope;
    private final EmployerContext employerContext;
    private final InvitationService invitationService;
    /** Present when the build wrote META-INF/build-info.properties. */
    private final ObjectProvider<BuildProperties> buildProperties;

    private final MembershipService membershipService;
    private final PictureService pictures;
    private final String projectUrl;

    public UiContextFactory(CurrentUserService currentUserService,
                            Scope scope,
                            EmployerContext employerContext,
                            InvitationService invitationService,
                            ObjectProvider<BuildProperties> buildProperties,
                            MembershipService membershipService,
                            PictureService pictures,
                            @Value("${app.project-url}") String projectUrl) {
        this.currentUserService = currentUserService;
        this.scope = scope;
        this.employerContext = employerContext;
        this.invitationService = invitationService;
        this.buildProperties = buildProperties;
        this.membershipService = membershipService;
        this.pictures = pictures;
        this.projectUrl = projectUrl;
    }

    public UiContext build(HttpServletRequest request) {
        Actor user = currentUserService.current();
        // Through the scope, so the shell and the screen read the list once between them.
        List<Employer> employers = scope.visibleEmployers();
        Ref active = activeEmployer(employers);

        String path = request.getRequestURI();
        // The sidebar holds what belongs to the active employer, and nothing else
        // (spec 7.3): the switcher heads it, and every entry covers that employer.
        String profile = active == null ? null : "/employers/" + active.id();
        List<NavItem> nav = new java.util.ArrayList<>(List.of(
                new NavItem("layout-dashboard", "nav.dashboard", "/", path.equals("/")),
                new NavItem("briefcase", "nav.jobs", "/jobs", path.startsWith("/jobs")),
                new NavItem("rss", "nav.feeds", "/feeds", path.startsWith("/feeds")),
                new NavItem("users", "nav.people", "/people",
                        path.startsWith("/people") || path.equals(profile + "/people"))));
        // The one entry whose visibility depends on the membership role: holding the
        // token list is close to holding the access (spec 7.17). An employer is
        // active whenever the user has one; with none there is no role to check.
        if (active != null
                && membershipService.canAdminister(user, UUID.fromString(active.id()))) {
            nav.add(new NavItem("key", "nav.tokens", "/tokens", path.startsWith("/tokens")));
        }
        if (profile != null) {
            nav.add(new NavItem("building-store", "nav.profile", profile,
                    onProfile(path, profile)));
        }
        nav.add(new NavItem("map-pin", "nav.locations", "/locations", path.startsWith("/locations")));
        nav.add(new NavItem("tag", "nav.tags", "/tags", path.startsWith("/tags")));
        // The employer's log (spec 7.21); a person's own is in the user menu instead.
        nav.add(new NavItem("history", "nav.activity", "/activity", path.equals("/activity")));

        // What exists without an employer goes in the top bar (spec 7.3).
        List<NavItem> top = new java.util.ArrayList<>(List.of(
                new NavItem("building", "nav.employers", "/employers",
                        path.startsWith("/employers") && !onProfile(path, profile)
                                && !path.equals(profile + "/people")),
                // Stays visible when empty: a user with no memberships has nothing
                // else to do, and an entry that vanishes cannot be checked (spec 7.3).
                new NavItem("mail", "nav.invitations", "/invitations", path.startsWith("/invitations"))));
        // Platform staff only (spec 7.20). While viewing as someone the actor is that
        // user, never an admin, so the entry is gone and the banner is the way back.
        if (user.isAdmin()) {
            top.add(new NavItem("user", "nav.users", "/users", path.startsWith("/users")));
        }
        UserEntity viewing = currentUserService.viewedUser().orElse(null);

        // While viewing as someone, the actor is them: theirs is the picture shown.
        String pictureUrl = user.isAnonymous() || user.isToken() ? null
                : pictures.urlFor(user.getId()).orElse(null);

        return new UiContext(user.getDisplayName(), Avatar.of(pictureUrl, user.getDisplayName()), user.isAdmin(),
                currentUserService.isDevMode(), employerContext.getTheme(),
                active, employers.stream().map(e -> new Ref(e.getId().toString(), e.getName())).toList(),
                nav, top, WebLangConfig.LANGUAGES, invitationService.countPendingFor(user),
                version(), projectUrl,
                viewing == null ? null : user.getDisplayName(),
                viewing == null ? null : viewing.getEmail(),
                currentUserService.canSwitchAdminMode());
    }

    /** The build's version; "development" when run from classes with no build info. */
    private String version() {
        BuildProperties build = buildProperties.getIfAvailable();
        return build == null ? "development" : build.getVersion();
    }

    /** The active employer's own record and its edit and delete pages, not its People screen. */
    private static boolean onProfile(String path, String profile) {
        return profile != null && (path.equals(profile) || path.startsWith(profile + "/"))
                && !path.equals(profile + "/people");
    }

    /** The same employer the screens cover (spec 2.5), so the switcher cannot name another. */
    private Ref activeEmployer(List<Employer> employers) {
        return employerContext.resolve(employers)
                .map(e -> new Ref(e.getId().toString(), e.getName()))
                .orElse(null);
    }
}
