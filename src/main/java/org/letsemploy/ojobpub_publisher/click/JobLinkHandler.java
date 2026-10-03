package org.letsemploy.ojobpub_publisher.click;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.letsemploy.ojobpub_publisher.config.WebLangConfig;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.JobService;
import org.letsemploy.ojobpub_publisher.job.Publication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/**
 * A job's public link, {@code /go/{jobId}} (spec 5.6): what the published
 * document gives as the job's URL. A published job's link counts the click and
 * redirects to the employer's own page; any other job's link leads nowhere.
 *
 * <p>A functional endpoint, not a {@code @Controller}, on purpose. Every
 * controller in this application gets the back-office shell from
 * {@code UiContextAdvice}, which resolves a user and opens a session - and this
 * URL is public, anonymous and stateless like the feed (spec 5.1). For the same
 * reason the page's language comes from {@code Accept-Language}, not the session.
 */
@Configuration
public class JobLinkHandler {

    private static final Logger log = LoggerFactory.getLogger(JobLinkHandler.class);

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** The languages the bundles hold; the first, English, is the fallback. */
    private static final List<Locale> LANGUAGES = WebLangConfig.LANGUAGES.stream()
            .map(Locale::forLanguageTag).toList();

    private final JobService jobService;
    private final JobClickService clicks;
    private final ClickCountry country;
    private final ITemplateEngine templates;

    public JobLinkHandler(JobService jobService, JobClickService clicks, ClickCountry country,
                          ITemplateEngine templates) {
        this.jobService = jobService;
        this.clicks = clicks;
        this.country = country;
        this.templates = templates;
    }

    @Bean
    RouterFunction<ServerResponse> jobLinks() {
        // HEAD explicitly: a functional GET does not answer it, and link checkers use it.
        return RouterFunctions.route()
                .GET("/go/{id}", this::follow)
                .HEAD("/go/{id}", this::follow)
                .build();
    }

    ServerResponse follow(ServerRequest request) {
        String id = request.pathVariable("id");
        if (!UUID_PATTERN.matcher(id).matches()) {
            return page(request, HttpStatus.NOT_FOUND, null);
        }
        UUID jobId = UUID.fromString(id.toLowerCase(Locale.ROOT));
        Optional<Job> found = jobService.findForLinking(jobId);
        if (found.isEmpty()) {
            return page(request, HttpStatus.NOT_FOUND, null);
        }
        // One spelling per link, as for a feed (spec 5.1).
        if (!id.equals(jobId.toString())) {
            return ServerResponse.status(HttpStatus.MOVED_PERMANENTLY)
                    .header(HttpHeaders.LOCATION, "/go/" + jobId)
                    .build();
        }
        Job job = found.get();

        // The link is the employer's control: once the job leaves the feed, a copy
        // of the feed held anywhere no longer leads to it (spec 5.6).
        if (!Publication.isPublishable(job, LocalDate.now())) {
            return page(request, HttpStatus.GONE, job.getEmployer());
        }
        if (!isWebAddress(job.getUrl())) {
            log.warn("Job {} is published with a URL that is not a web address; its link answers 410", job.getId());
            return page(request, HttpStatus.GONE, job.getEmployer());
        }

        HttpServletRequest servlet = request.servletRequest();
        // A HEAD is a check, not a visit.
        if ("GET".equals(servlet.getMethod()) && clicks.counts(servlet.getHeader(HttpHeaders.USER_AGENT))) {
            try {
                clicks.record(job.getId(), job.getEmployer().getId(), country.of(servlet));
            } catch (RuntimeException e) {
                // A statistic is never worth a visitor's way to the job.
                log.warn("A click on job {} was not counted", job.getId(), e);
            }
        }
        return ServerResponse.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, job.getUrl())
                .headers(JobLinkHandler::linkHeaders)
                .build();
    }

    /** Only a page on the web is a destination; the form requires one, the database may not. */
    static boolean isWebAddress(String url) {
        if (url == null || url.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    /** Every answer is fetched afresh, so each click reaches the counter, and none is indexed. */
    private static void linkHeaders(HttpHeaders headers) {
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Robots-Tag", "noindex");
    }

    /** The page a person sees instead of the job: gone (410) or unknown (404). */
    private ServerResponse page(ServerRequest request, HttpStatus status, Employer employer) {
        HttpServletRequest servlet = request.servletRequest();
        // Thymeleaf's link expressions need the exchange; the response is only read, never written.
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder
                .currentRequestAttributes()).getResponse();
        Locale locale = Locale.lookup(request.headers().acceptLanguage(), LANGUAGES);
        if (locale == null) {
            locale = LANGUAGES.getFirst();
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put("gone", status == HttpStatus.GONE);
        variables.put("employerName", employer == null ? null : employer.getName());
        variables.put("employerUrl", employer == null || !isWebAddress(employer.getUrl()) ? null : employer.getUrl());
        WebContext context = new WebContext(JakartaServletWebApplication
                .buildApplication(servlet.getServletContext())
                .buildExchange(servlet, response), locale, variables);
        String html = templates.process("click/gone", context);
        return ServerResponse.status(status)
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_LANGUAGE, locale.getLanguage())
                .headers(JobLinkHandler::linkHeaders)
                .body(html);
    }
}
