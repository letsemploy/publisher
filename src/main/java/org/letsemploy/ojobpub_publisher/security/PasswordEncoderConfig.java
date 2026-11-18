package org.letsemploy.ojobpub_publisher.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hashes the passwords of local accounts (spec 2.12). Service token secrets are
 * random, not chosen, and stored as SHA-256 by {@code ServiceTokenService}, which
 * does not use it (spec 2.8).
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
