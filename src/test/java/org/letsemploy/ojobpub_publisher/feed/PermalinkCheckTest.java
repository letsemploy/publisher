package org.letsemploy.ojobpub_publisher.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.feed.PermalinkCheck.Result;

/**
 * Checking the employer's website (spec 7.23): what it answers at
 * /.well-known/ojobpub.json, told apart by status, Location and the permalink
 * header. A local server stands in for the website; no Spring, no database.
 */
class PermalinkCheckTest {

    private static final String BASE = "https://publisher.example";
    private static final UUID ID = UUID.randomUUID();
    private static final String OURS = BASE + "/ojobpub/v1/permalink/" + ID + "/ojobpub.json";

    private HttpServer server;
    private PermalinkCheck check;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/proxy", ex -> answer(ex, 200, "application/json", ID.toString()));
        server.createContext("/stranger", ex -> answer(ex, 200, "application/json", UUID.randomUUID().toString()));
        server.createContext("/copy", ex -> answer(ex, 200, "application/json", null));
        server.createContext("/cms", ex -> answer(ex, 200, "text/html; charset=utf-8", null));
        server.createContext("/missing", ex -> answer(ex, 404, "text/html", null));
        server.createContext("/broken", ex -> answer(ex, 500, "text/html", null));
        server.createContext("/to-us", ex -> redirect(ex, OURS));
        server.createContext("/to-a-feed", ex -> redirect(ex, BASE + "/ojobpub/v1/acme_x/all_y/ojobpub.json"));
        server.createContext("/to-proxy", ex -> redirect(ex, "/proxy"));
        server.createContext("/loop", ex -> redirect(ex, "/loop"));
        server.createContext("/elsewhere", ex -> redirect(ex, "http://127.0.0.2:1/x"));
        server.start();
        // Only this server counts as public here.
        check = new PermalinkCheck(BASE, uri -> uri.getPort() == server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void tellsWhatTheWebsiteServes() {
        assertThat(at("/proxy")).isEqualTo(Result.PROXY);
        assertThat(at("/to-us")).isEqualTo(Result.REDIRECT);
        assertThat(at("/to-proxy")).as("through a redirect of its own").isEqualTo(Result.REDIRECT);
        assertThat(at("/stranger")).isEqualTo(Result.OTHER_DOCUMENT);
        assertThat(at("/to-a-feed")).isEqualTo(Result.OTHER_DOCUMENT);
        assertThat(at("/copy")).isEqualTo(Result.STATIC_COPY);
        assertThat(at("/cms")).isEqualTo(Result.NOT_OJOBPUB);
        assertThat(at("/missing")).isEqualTo(Result.NOT_FOUND);
        assertThat(at("/broken")).isEqualTo(Result.HTTP_ERROR);
        assertThat(at("/loop")).isEqualTo(Result.HTTP_ERROR);
        assertThat(Result.PROXY.works()).isTrue();
        assertThat(Result.REDIRECT.works()).isTrue();
        assertThat(Result.STATIC_COPY.works()).isFalse();
    }

    @Test
    void asksOnlyPublicHttpsAndFollowsNoRedirectPastIt() {
        assertThat(at("/elsewhere")).isEqualTo(Result.NOT_ALLOWED);
        PermalinkCheck fenced = new PermalinkCheck(BASE);
        assertThat(fenced.check("localhost", ID)).isEqualTo(Result.NOT_ALLOWED);
        assertThat(fenced.check(URI.create("http://8.8.8.8/.well-known/ojobpub.json"), ID)).isEqualTo(Result.NOT_ALLOWED);
    }

    @Test
    void aWebsiteThatDoesNotAnswerIsUnreachable() throws IOException {
        int closed;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closed = socket.getLocalPort();
        }
        PermalinkCheck anywhere = new PermalinkCheck(BASE, uri -> true);
        assertThat(anywhere.check(URI.create("http://127.0.0.1:" + closed + "/.well-known/ojobpub.json"), ID))
                .isEqualTo(Result.UNREACHABLE);
    }

    private Result at(String path) {
        return check.check(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path), ID);
    }

    private static void answer(HttpExchange ex, int status, String type, String permalink) throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        if (permalink != null) {
            ex.getResponseHeaders().add(PermalinkCheck.HEADER, permalink);
        }
        byte[] body = "{}".getBytes();
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }

    private static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().add("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }
}
