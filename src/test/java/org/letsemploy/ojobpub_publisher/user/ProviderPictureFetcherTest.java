package org.letsemploy.ojobpub_publisher.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Copying a provider's picture (spec 7.27): an address a user may choose, so
 * fenced - https, public addresses, no redirects, a size limit, and an image or
 * nothing. A local server stands in for the provider; no Spring, no database.
 */
class ProviderPictureFetcherTest {

    private static final UUID USER = UUID.randomUUID();

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private PictureService pictures;
    private byte[] png;

    @BeforeEach
    void start() throws IOException {
        png = PictureProcessorTest.encode(new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "png");
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/picture.png", exchange -> answer(exchange, 200, png));
        server.createContext("/page.html", exchange ->
                answer(exchange, 200, "<html>hello</html>".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/missing.png", exchange -> answer(exchange, 404, new byte[0]));
        server.createContext("/huge.png", exchange -> answer(exchange, 200, new byte[PictureProcessor.MAX_BYTES + 10]));
        server.createContext("/moved.png", exchange -> {
            hits.incrementAndGet();
            exchange.getResponseHeaders().add("Location", base() + "/picture.png");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        pictures = mock(PictureService.class);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void answer(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body) throws IOException {
        hits.incrementAndGet();
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Fetches synchronously, from the local server, which the real rule refuses. */
    private ProviderPictureFetcher anyAddress() {
        return new ProviderPictureFetcher(pictures, Runnable::run, uri -> true);
    }

    @Test
    void aPictureIsFetchedProcessedAndStored() {
        String url = base() + "/picture.png";
        anyAddress().fetch(USER, url);

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        verify(pictures).storeProviderPicture(eq(USER), eq(url), stored.capture());
        // Re-encoded, not the bytes that came: a JPEG, never the PNG served.
        assertThat(stored.getValue()).startsWith((byte) 0xFF, (byte) 0xD8);
    }

    @Test
    void whatIsNotAPictureIsDropped() {
        anyAddress().fetch(USER, base() + "/page.html");
        anyAddress().fetch(USER, base() + "/missing.png");
        anyAddress().fetch(USER, base() + "/huge.png");
        verify(pictures, never()).storeProviderPicture(any(), any(), any());
    }

    @Test
    void aRedirectIsNotFollowed() {
        anyAddress().fetch(USER, base() + "/moved.png");
        assertThat(hits).hasValue(1);
        verify(pictures, never()).storeProviderPicture(any(), any(), any());
    }

    @Test
    void theRealRuleNeverAsksThisServer() {
        new ProviderPictureFetcher(pictures, Runnable::run).fetch(USER, base() + "/picture.png");
        new ProviderPictureFetcher(pictures, Runnable::run)
                .fetch(USER, "https://127.0.0.1:" + server.getAddress().getPort() + "/picture.png");
        assertThat(hits).hasValue(0);
        verify(pictures, never()).storeProviderPicture(any(), any(), any());
    }

    @Test
    void noAddressDropsTheProviderCopy() {
        anyAddress().fetch(USER, null);
        verify(pictures).dropProviderPicture(USER);
    }

    @Test
    void onlyHttpsToPublicAddresses() {
        assertThat(ProviderPictureFetcher.isPublicHttps(URI.create("https://8.8.8.8/a.png"))).isTrue();
        assertThat(ProviderPictureFetcher.isPublicHttps(URI.create("http://8.8.8.8/a.png"))).isFalse();
        assertThat(ProviderPictureFetcher.isPublicHttps(URI.create("https://me@8.8.8.8/a.png"))).isFalse();
        assertThat(ProviderPictureFetcher.isPublicHttps(URI.create("file:///etc/passwd"))).isFalse();
        assertThat(ProviderPictureFetcher.isPublicHttps(URI.create("https://localhost/a.png"))).isFalse();
        assertThat(ProviderPictureFetcher.isPublicHttps(URI.create("https://[::1]/a.png"))).isFalse();
        assertThat(ProviderPictureFetcher.isPublicHttps(URI.create("https://169.254.169.254/latest/meta-data"))).isFalse();
    }

    @Test
    void privateAndSpecialAddressesAreNotPublic() throws IOException {
        for (String address : new String[] {"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254",
                "100.64.0.1", "0.0.0.0", "224.0.0.1", "255.255.255.255", "198.18.0.1",
                "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1"}) {
            assertThat(ProviderPictureFetcher.isPublic(InetAddress.getByName(address))).as(address).isFalse();
        }
        for (String address : new String[] {"8.8.8.8", "140.82.112.3", "2001:4860:4860::8888"}) {
            assertThat(ProviderPictureFetcher.isPublic(InetAddress.getByName(address))).as(address).isTrue();
        }
    }
}
