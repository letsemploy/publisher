package org.letsemploy.ojobpub_publisher.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * Viewing as another user is read-only (spec 2.9): while it lasts, every request
 * that could change something is refused, here, in one place, for every screen.
 *
 * <p>Nothing may be done in someone else's name: not a job edited on their
 * behalf, and above all not an invitation accepted or an employer created, which
 * would grant them a membership without their consent (spec 2.2). Only what
 * changes the admin's own view gets through: stopping, the employer switcher and
 * the theme. Logging out is Spring Security's, before any of this.
 *
 * <p>The management API cannot reach this: its chain is stateless and
 * token-authenticated, with no session to hold an impersonation.
 */
@Configuration
@RequiredArgsConstructor
public class ImpersonationGuard implements HandlerInterceptor, WebMvcConfigurer {

    static final String STOP = "/impersonation/stop";
    private static final Set<String> ALLOWED = Set.of(STOP, "/context/employer", "/context/theme");
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final CurrentUserService currentUserService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (SAFE_METHODS.contains(request.getMethod())
                || ALLOWED.contains(request.getRequestURI().substring(request.getContextPath().length()))
                // Cheap and session-free first: most requests - every API call among
                // them - have no impersonation, and need no user lookup to know it.
                || Impersonation.current().isEmpty()
                || !currentUserService.isImpersonating()) {
            return true;
        }
        FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
        flash.put("errorMsg", "impersonation.readOnly");
        String referer = request.getHeader("Referer");
        String target = referer != null && referer.startsWith(baseUrl(request)) ? referer : request.getContextPath() + "/";
        RequestContextUtils.saveOutputFlashMap(target, request, response);
        response.setStatus(HttpStatus.SEE_OTHER.value());
        response.setHeader("Location", target);
        return false;
    }

    /** Only redirect back to this application, never to wherever a Referer claims. */
    private static String baseUrl(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort());
    }
}
