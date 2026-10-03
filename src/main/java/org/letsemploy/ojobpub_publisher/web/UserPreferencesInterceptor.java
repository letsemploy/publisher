package org.letsemploy.ojobpub_publisher.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.letsemploy.ojobpub_publisher.security.Impersonation;
import org.letsemploy.ojobpub_publisher.user.SettingsService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * A person's saved settings follow them into every new session (spec 7.26).
 *
 * <p>Applied once per session, behind a marker, so a request costs nothing once
 * it is done; and only for someone signed in, so a session that began on the
 * sign-in page gets them at its first request after. Never while an admin views
 * as someone (spec 2.9): the admin keeps their own. Not registered for the public
 * URLs, which must open no session (spec 5.1).
 */
@Component
public class UserPreferencesInterceptor implements HandlerInterceptor {

    static final String APPLIED = UserPreferencesInterceptor.class.getName() + ".APPLIED";

    private final CurrentUserService currentUserService;
    private final SettingsService settings;
    private final UserPreferences preferences;

    public UserPreferencesInterceptor(CurrentUserService currentUserService,
                                      SettingsService settings,
                                      UserPreferences preferences) {
        this.currentUserService = currentUserService;
        this.settings = settings;
        this.preferences = preferences;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(APPLIED) != null || Impersonation.current().isPresent()) {
            return true;
        }
        Actor actor = currentUserService.current();
        if (actor.isAnonymous() || actor.isToken()) {
            return true;
        }
        preferences.apply(request, response, settings.self(actor), false);
        session.setAttribute(APPLIED, Boolean.TRUE);
        return true;
    }
}
