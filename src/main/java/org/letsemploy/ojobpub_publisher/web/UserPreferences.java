package org.letsemploy.ojobpub_publisher.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.ZoneId;
import java.util.Locale;
import java.util.TimeZone;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.springframework.context.i18n.SimpleTimeZoneAwareLocaleContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleContextResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

import static org.springframework.web.servlet.i18n.SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME;
import static org.springframework.web.servlet.i18n.SessionLocaleResolver.TIME_ZONE_SESSION_ATTRIBUTE_NAME;

/**
 * Puts a person's saved settings (spec 7.26) into their session, where the
 * screens read them: the language and time zone into the locale resolver, the
 * theme into {@link EmployerContext}.
 */
@Component
public class UserPreferences {

    private final EmployerContext employerContext;

    public UserPreferences(EmployerContext employerContext) {
        this.employerContext = employerContext;
    }

    /**
     * @param replace whether a setting left unchosen clears what the session holds:
     *                true after saving the settings page; false at the start of a
     *                session, where a language picked on the sign-in page should stay
     */
    public void apply(HttpServletRequest request, HttpServletResponse response, UserEntity user, boolean replace) {
        HttpSession session = request.getSession(false);
        Locale locale = user.getLanguage() != null ? Locale.forLanguageTag(user.getLanguage())
                : replace || session == null ? null : (Locale) session.getAttribute(LOCALE_SESSION_ATTRIBUTE_NAME);
        TimeZone zone = user.getTimeZone() != null ? TimeZone.getTimeZone(ZoneId.of(user.getTimeZone()))
                : replace || session == null ? null : (TimeZone) session.getAttribute(TIME_ZONE_SESSION_ATTRIBUTE_NAME);
        if (RequestContextUtils.getLocaleResolver(request) instanceof LocaleContextResolver contexts) {
            contexts.setLocaleContext(request, response, new SimpleTimeZoneAwareLocaleContext(locale, zone));
        }
        if (user.getTheme() != null) {
            employerContext.setTheme(user.getTheme());
        } else if (replace) {
            employerContext.setTheme("auto");
        }
    }
}
