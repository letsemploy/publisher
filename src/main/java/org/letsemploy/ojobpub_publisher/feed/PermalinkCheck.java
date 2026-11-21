package org.letsemploy.ojobpub_publisher.feed;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.letsemploy.ojobpub_publisher.common.PublicAddresses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Asks the employer's website what it serves at {@code /.well-known/ojobpub.json}
 * (spec 7.23), and says whether that is this permalink - by a reverse proxy, by a
 * redirect, or not at all and why.
 *
 * <p>The host is typed by a member, so this is a request to an address a user
 * chooses and is fenced like the provider picture (spec 7.27): {@code https} to
 * public addresses only, each redirect followed by hand and checked again, a time
 * limit, and the body is never read. What comes back is told apart by its status,
 * its {@code Location} and the {@link #HEADER} the permalink route sends.
 */
@Component
public class PermalinkCheck {

    /** Sent with every permalink document, naming the permalink (never the feed, spec 5.5). */
    public static final String HEADER = "OJobPub-Permalink";

    /** What the website answered. The first two mean it works. */
    public enum Result {
        /** Serves this permalink itself: every consumer gets it. */
        PROXY,
        /** Redirects to it: works for a consumer that follows redirects. */
        REDIRECT,
        /** Serves or redirects to another of this application's documents. */
        OTHER_DOCUMENT,
        /** JSON, but not from here: a copy of the file, or a proxy that drops the header. */
        STATIC_COPY,
        /** Something else, typically the site's own HTML page. */
        NOT_OJOBPUB,
        NOT_FOUND,
        /** Any other status, or too many redirects. */
        HTTP_ERROR,
        /** No answer: name, connection, TLS or time limit. */
        UNREACHABLE,
        /** Not https, or not a public address - never asked. */
        NOT_ALLOWED;

        public boolean works() {
            return this == PROXY || this == REDIRECT;
        }
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_REDIRECTS = 5;
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

    private final String baseUrl;
    private final Predicate<URI> allowed;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(TIMEOUT)
            .build();

    @Autowired
    public PermalinkCheck(@Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this(baseUrl, PublicAddresses::isPublicHttps);
    }

    /** With another rule for which addresses may be asked: tests serve from localhost. */
    PermalinkCheck(String baseUrl, Predicate<URI> allowed) {
        this.baseUrl = baseUrl;
        this.allowed = allowed;
    }

    /** Checks {@code https://{host}/.well-known/ojobpub.json}. */
    public Result check(String host, UUID permalinkId) {
        return check(URI.create("https://" + host + WebserverConfig.PATH), permalinkId);
    }

    Result check(URI start, UUID permalinkId) {
        String ours = baseUrl + "/ojobpub/v1/permalink/" + permalinkId + "/ojobpub.json";
        String ourDocuments = baseUrl + "/ojobpub/v1/";
        URI uri = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!allowed.test(uri)) {
                return Result.NOT_ALLOWED;
            }
            HttpResponse<InputStream> response;
            try {
                response = ask(uri);
            } catch (IOException e) {
                return Result.UNREACHABLE;
            }
            int status = response.statusCode();
            if (REDIRECTS.contains(status)) {
                Optional<String> location = response.headers().firstValue("Location");
                if (location.isEmpty()) {
                    return Result.HTTP_ERROR;
                }
                URI next;
                try {
                    next = uri.resolve(location.get());
                } catch (IllegalArgumentException e) {
                    return Result.HTTP_ERROR;
                }
                // Recognised by its address, not fetched: it is us.
                if (next.toString().equals(ours)) {
                    return Result.REDIRECT;
                }
                if (next.toString().startsWith(ourDocuments)) {
                    return Result.OTHER_DOCUMENT;
                }
                uri = next;
                continue;
            }
            if (status == 404 || status == 410) {
                return Result.NOT_FOUND;
            }
            if (status != 200) {
                return Result.HTTP_ERROR;
            }
            Optional<String> served = response.headers().firstValue(HEADER);
            if (served.isPresent()) {
                if (!served.get().trim().equalsIgnoreCase(permalinkId.toString())) {
                    return Result.OTHER_DOCUMENT;
                }
                // Reached through a redirect of the website's own: a consumer must follow it.
                return hop == 0 ? Result.PROXY : Result.REDIRECT;
            }
            String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            return type.contains("json") ? Result.STATIC_COPY : Result.NOT_OJOBPUB;
        }
        return Result.HTTP_ERROR;
    }

    /** Status and headers only: the body is closed unread. */
    private HttpResponse<InputStream> ask(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "ojobpub-publisher")
                .GET()
                .build();
        CompletableFuture<HttpResponse<InputStream>> pending =
                http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        try {
            HttpResponse<InputStream> response = pending.get(TIMEOUT.multipliedBy(2).toMillis(), TimeUnit.MILLISECONDS);
            response.body().close();
            return response;
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new IOException("Timed out", e);
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
        }
    }
}
