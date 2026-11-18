package org.letsemploy.ojobpub_publisher.config;

import java.time.ZoneId;
import org.letsemploy.ojobpub_publisher.job.Publication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * The zone "today" is evaluated in for publication (spec 4.4), from
 * {@code app.zone}; empty means the server's. An unknown zone fails startup.
 *
 * <p>Not the JVM's default zone: without {@code hibernate.jdbc.time_zone}, moving
 * that would shift how stored instants are read back.
 */
@Configuration(proxyBeanMethods = false)
public class PublicationZoneConfig {

    public PublicationZoneConfig(@Value("${app.zone:}") String zone) {
        Publication.useZone(zone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zone.trim()));
    }
}
