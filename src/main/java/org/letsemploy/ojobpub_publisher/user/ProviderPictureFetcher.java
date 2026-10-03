package org.letsemploy.ojobpub_publisher.user;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Copies a provider's picture into the application (spec 7.27), after the
 * sign-in that noticed it has committed, and off the request: nobody waits for
 * a download, and a failed one changes nothing.
 *
 * <p>The address comes from the identity provider, and at some providers the
 * person edits it themselves - so this is a request to an address a user may
 * choose, and is fenced accordingly: {@code https} only, to public addresses
 * only, no redirects, a time limit and a size limit, and what comes back must
 * decode as an image or it is dropped. The address is never logged: it is the
 * provider's data, and may identify the person.
 */
@Component
public class ProviderPictureFetcher {

    private static final Logger log = LoggerFactory.getLogger(ProviderPictureFetcher.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final PictureService pictures;
    private final Executor executor;
    private final Predicate<URI> allowed;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(TIMEOUT)
            .build();

    @Autowired
    public ProviderPictureFetcher(PictureService pictures,
                                  @Qualifier("applicationTaskExecutor") Executor executor) {
        this(pictures, executor, ProviderPictureFetcher::isPublicHttps);
    }

    /** With another rule for which addresses may be fetched: tests serve from localhost. */
    ProviderPictureFetcher(PictureService pictures, Executor executor, Predicate<URI> allowed) {
        this.pictures = pictures;
        this.executor = executor;
        this.allowed = allowed;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onChanged(PictureService.ProviderPictureChanged changed) {
        executor.execute(() -> fetch(changed.userId(), changed.url()));
    }

    /** Fetches and stores the picture, or drops the copy when there is no address any more. */
    void fetch(UUID userId, String url) {
        if (url == null) {
            pictures.dropProviderPicture(userId);
            return;
        }
        try {
            URI uri = new URI(url);
            if (!allowed.test(uri)) {
                log.info("Not fetching the provider picture of user {}: the address is not allowed", userId);
                return;
            }
            byte[] processed = PictureProcessor.process(download(uri));
            pictures.storeProviderPicture(userId, url, processed);
            log.debug("Stored the provider picture of user {}", userId);
        } catch (PictureProcessor.Rejected e) {
            log.info("Not storing the provider picture of user {}: {}", userId, e.reason());
        } catch (URISyntaxException | IOException | RuntimeException e) {
            log.info("Could not fetch the provider picture of user {}: {}", userId, e.getClass().getSimpleName());
        }
    }

    private byte[] download(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept", "image/jpeg, image/png, image/gif")
                .GET()
                .build();
        // One byte past the limit is enough to know it is too big.
        CompletableFuture<HttpResponse<byte[]>> pending = http.sendAsync(request,
                HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(),
                        PictureProcessor.MAX_BYTES + 1L));
        try {
            // The whole exchange, body included: a server that trickles is cut off.
            HttpResponse<byte[]> response = pending.get(TIMEOUT.multipliedBy(2).toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                throw new IOException("Status " + response.statusCode());
            }
            return response.body();
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

    /**
     * {@code https}, to a host whose every address is public. Checked by
     * resolving the name first; a name that resolves elsewhere a moment later can
     * still slip past, which the image check after it does not undo but limits to
     * a request whose answer nobody sees.
     */
    static boolean isPublicHttps(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            for (InetAddress address : addresses) {
                if (!isPublic(address)) {
                    return false;
                }
            }
            return addresses.length > 0;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first != 0                                         // "this" network
                    && !(first == 100 && second >= 64 && second < 128) // carrier-grade NAT
                    && !(first == 192 && second == 0 && (b[2] & 0xff) == 0) // IETF protocol assignments
                    && !(first == 198 && (second == 18 || second == 19))  // benchmarking
                    && first < 240;                                    // reserved, broadcast
        }
        if (address instanceof Inet6Address) {
            // Unique local fc00::/7; and an IPv4 address in IPv6 dress is judged as itself.
            if ((b[0] & 0xfe) == 0xfc) {
                return false;
            }
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= b[i] == 0;
            }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                try {
                    return isPublic(InetAddress.getByAddress(Arrays.copyOfRange(b, 12, 16)));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
