package org.letsemploy.ojobpub_publisher.security;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * The development bypass disables authentication entirely (spec 2.3). It is bound
 * to the dev profile, and the application must refuse to start if that profile is
 * ever active alongside prod - an unauthenticated back-office in production would
 * otherwise be one careless profile argument away.
 */
@Configuration
@Profile("dev")
public class DevBypassGuard {

    private static final Logger log = LoggerFactory.getLogger(DevBypassGuard.class);

    private final Environment environment;

    public DevBypassGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void refuseToRunUnauthenticatedInProduction() {
        List<String> active = List.of(environment.getActiveProfiles());
        if (active.contains("prod")) {
            throw new IllegalStateException(
                    "The dev profile disables authentication and must never be combined with prod. "
                            + "Active profiles: " + active);
        }
        log.warn("Development mode: authentication is DISABLED. Do not use this configuration in production.");
    }
}
