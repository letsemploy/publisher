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
        // A picture is on the https port. Any other would make the fetch a probe of
        // that port on a public host, answered by whether the connection opens.
        if (uri.getPort() != -1 && uri.getPort() != 443) {
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
            int third = b[2] & 0xff;
            return first != 0                                         // "this" network
                    && !(first == 100 && second >= 64 && second < 128) // carrier-grade NAT
                    && !(first == 192 && second == 0 && third == 0)   // IETF protocol assignments
                    && !(first == 192 && second == 0 && third == 2)   // documentation
                    && !(first == 198 && second == 51 && third == 100) // documentation
                    && !(first == 203 && second == 0 && third == 113) // documentation
                    && !(first == 192 && second == 88 && third == 99) // 6to4 relays, deprecated
                    && !(first == 198 && (second == 18 || second == 19))  // benchmarking
                    && first < 240;                                    // reserved, broadcast
        }
        if (address instanceof Inet6Address) {
            return isPublicIpv6(b);
        }
        return false;
    }

    /**
     * Allowed, not refused: only global unicast {@code 2000::/3} is public, less
     * the special ranges inside it. A refusal list missed every form that carries
     * an IPv4 address, and those reach whatever that address is.
     */
    private static boolean isPublicIpv6(byte[] b) {
        // Two forms carry an IPv4 address in their last four bytes and reach it:
        // mapped ::ffff:0:0/96 (Java mostly hands those over as IPv4 already), and
        // NAT64 64:ff9b::/96, which on an IPv6-only network with DNS64 is how every
        // IPv4 host is reached - so a public one must still be fetched.
        if (startsWith(b, new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff})
                || startsWith(b, new byte[] {0, 0x64, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0})) {
            try {
                return isPublic(InetAddress.getByAddress(Arrays.copyOfRange(b, 12, 16)));
            } catch (UnknownHostException e) {
                return false;
            }
        }
        int first = ((b[0] & 0xff) << 8) | (b[1] & 0xff);
        int second = ((b[2] & 0xff) << 8) | (b[3] & 0xff);
        if ((first & 0xe000) != 0x2000) {
            return false;                                  // not 2000::/3: local, multicast, IPv4-compatible...
        }
        return !(first == 0x2001 && second < 0x0200)       // 2001::/23 IETF protocol assignments, Teredo among them
                && !(first == 0x2001 && second == 0x0db8)  // 2001:db8::/32 documentation
                && first != 0x2002                         // 2002::/16 6to4, deprecated, carries an IPv4 address
                && !(first == 0x3fff && second < 0x1000)   // 3fff::/20 documentation
                && first != 0x5f00;                        // 5f00::/16 SRv6 SIDs
    }

    private static boolean startsWith(byte[] address, byte[] prefix) {
        return Arrays.equals(address, 0, prefix.length, prefix, 0, prefix.length);
    }
}
