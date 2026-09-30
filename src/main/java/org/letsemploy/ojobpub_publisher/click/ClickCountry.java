package org.letsemploy.ojobpub_publisher.click;

import com.maxmind.geoip2.DatabaseReader;
import com.neovisionaries.i18n.CountryCode;
import jakarta.servlet.http.HttpServletRequest;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The country a click came from (spec 5.6): the header a proxy or CDN sets, if one
 * is configured and carries a country; else the GeoIP database, if one is
 * mounted; else unknown. The address is looked up and dropped, never stored or
 * logged (spec 10).
 *
 * <p>Behind a reverse proxy the remote address is the proxy's, unless
 * {@code server.forward-headers-strategy} is set (spec 9.5).
 */
@Component
public class ClickCountry implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ClickCountry.class);

    private final String header;
    private final DatabaseReader geoip;

    public ClickCountry(@Value("${app.clicks.country-header:}") String header,
                        @Value("${app.clicks.geoip-database:}") String database) {
        this.header = header.isBlank() ? null : header.strip();
        this.geoip = database.isBlank() ? null : open(database.strip());
        if (this.header == null && this.geoip == null) {
            log.info("Job-link clicks are counted without a country: set app.clicks.country-header"
                    + " or app.clicks.geoip-database to know it");
        }
    }

    /** A configured database that cannot be read is a mistake to see at startup, not a silent unknown. */
    private static DatabaseReader open(String path) {
        File file = new File(path);
        try {
            return new DatabaseReader.Builder(file).build();
        } catch (IOException e) {
            throw new IllegalStateException("app.clicks.geoip-database: cannot read " + file.getAbsolutePath(), e);
        }
    }

    /** An ISO 3166 alpha-2 code, upper case, or {@link JobClick#UNKNOWN_COUNTRY}. */
    public String of(HttpServletRequest request) {
        if (header != null) {
            Optional<String> fromHeader = country(request.getHeader(header));
            if (fromHeader.isPresent()) {
                return fromHeader.get();
            }
        }
        if (geoip != null) {
            try {
                // An address literal is parsed, never resolved: no DNS lookup here.
                InetAddress address = InetAddress.ofLiteral(request.getRemoteAddr());
                Optional<String> fromDatabase = geoip.tryCountry(address)
                        .flatMap(r -> country(r.country().isoCode()));
                if (fromDatabase.isPresent()) {
                    return fromDatabase.get();
                }
            } catch (IOException | RuntimeException | com.maxmind.geoip2.exception.GeoIp2Exception e) {
                log.debug("No country for a click: {}", e.getClass().getSimpleName());
            }
        }
        return JobClick.UNKNOWN_COUNTRY;
    }

    /**
     * Only an officially assigned code counts. Cloudflare's {@code XX} (unknown) and
     * {@code T1} (Tor), and whatever else a header may carry, become unknown.
     */
    static Optional<String> country(String value) {
        if (value == null || value.length() != 2) {
            return Optional.empty();
        }
        CountryCode code = CountryCode.getByCode(value.toUpperCase(Locale.ROOT));
        if (code == null || code.getAssignment() != CountryCode.Assignment.OFFICIALLY_ASSIGNED) {
            return Optional.empty();
        }
        return Optional.of(code.getAlpha2());
    }

    @Override
    public void close() throws IOException {
        if (geoip != null) {
            geoip.close();
        }
    }
}
