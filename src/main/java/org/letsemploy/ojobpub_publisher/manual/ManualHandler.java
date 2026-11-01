package org.letsemploy.ojobpub_publisher.manual;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The user manual's entry, {@code /docs} (spec 7.29). The manual itself is static,
 * built by Hugo into {@code static/docs/{lang}/}; this only picks the language and
 * redirects to its first page.
 *
 * <p>A functional endpoint, not a {@code @Controller}, for the reason
 * {@code JobLinkHandler} gives: every controller gets the back-office shell, which
 * opens a session, and the manual is public and opens none. So the language is not
 * read from the session either: the application's own links pass the one they show
 * as {@code ?lang=}, and anyone else gets their {@code Accept-Language}.
 */
@Configuration
public class ManualHandler {

    /** The manual's languages; the first, English, stands in for every other one. */
    public static final List<String> LANGUAGES = List.of("en", "de");

    private static final List<Locale> LOCALES = LANGUAGES.stream().map(Locale::forLanguageTag).toList();

    @Bean
    RouterFunction<ServerResponse> manual() {
        return RouterFunctions.route()
                .GET("/docs", this::enter)
                .GET("/docs/", this::enter)
                .HEAD("/docs", this::enter)
                .HEAD("/docs/", this::enter)
                .build();
    }

    private ServerResponse enter(ServerRequest request) {
        URI target = URI.create(request.servletRequest().getContextPath()
                + "/docs/" + language(request) + "/index.html");
        return ServerResponse.status(302).location(target).build();
    }

    /**
     * A language the application named wins, and one the manual lacks means English:
     * the page asking showed that language, so the browser's preference no longer
     * decides.
     */
    static String language(ServerRequest request) {
        Optional<String> asked = request.param("lang");
        if (asked.isPresent()) {
            return language(Locale.forLanguageTag(asked.get()));
        }
        Locale accepted = Locale.lookup(request.headers().acceptLanguage(), LOCALES);
        return accepted == null ? LANGUAGES.getFirst() : accepted.getLanguage();
    }

    /** The manual's language for a page shown in {@code locale}: its own if there, else English. */
    static String language(Locale locale) {
        return LANGUAGES.contains(locale.getLanguage()) ? locale.getLanguage() : LANGUAGES.getFirst();
    }
}
