package org.letsemploy.ojobpub_publisher.config;

import java.util.List;
import java.util.Locale;
import org.letsemploy.ojobpub_publisher.web.UserPreferencesInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

@Configuration
public class WebLangConfig implements WebMvcConfigurer {

    /** The languages there are bundles for (spec 8.1): the menu, the account screens and Settings offer these. */
    public static final List<String> LANGUAGES = List.of("en", "de", "fr", "it", "es", "pt", "ja", "ru", "zh");

    /** Neither the public URLs (spec 5.1, 5.6) nor static files are anybody's. */
    private static final String[] UNPERSONAL = {
            "/ojobpub/**", "/go/**", "/css/**", "/js/**", "/vendor/**", "/images/**", "/favicon.ico",
            "/actuator/**", "/graphql", "/graphql/schema"
    };

    private final UserPreferencesInterceptor userPreferences;

    public WebLangConfig(UserPreferencesInterceptor userPreferences) {
        this.userPreferences = userPreferences;
    }

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver lr = new SessionLocaleResolver();
        lr.setDefaultLocale(Locale.ENGLISH);
        return lr;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor lci = new LocaleChangeInterceptor();
        lci.setParamName("lang");
        return lci;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // First the person's saved settings, once per session (spec 7.26). Then
        // ?lang, which the signed-out pages offer - sign-in and the account screens
        // (spec 7.19, 7.24) - and which lasts the session; the public URLs have none.
        registry.addInterceptor(userPreferences).excludePathPatterns(UNPERSONAL);
        registry.addInterceptor(localeChangeInterceptor()).excludePathPatterns("/ojobpub/**", "/go/**", "/graphql/schema");
    }
}
