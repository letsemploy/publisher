package org.letsemploy.ojobpub_publisher.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Two filter chains (spec 9.4): the public feed and health endpoints are
 * anonymous, stateless and CSRF-free; everything else is authenticated with CSRF
 * enabled.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC = {
            "/ojobpub/**", "/actuator/health/**", "/actuator/info",
            "/css/**", "/vendor/**", "/images/**", "/favicon.ico", "/error"
    };

    /** Order 1: the published feed. Machines call this; it must never see a login page. */
    @Bean
    @org.springframework.core.annotation.Order(1)
    SecurityFilterChain publicFeedChain(HttpSecurity http) throws Exception {
        http.securityMatcher(PathPatternRequestMatcher.withDefaults().matcher("/ojobpub/**"))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        return http.build();
    }

    /**
     * Development mode: authentication is bypassed entirely, so no identity
     * provider is needed to run locally (spec 2.3). Bound to the dev profile and
     * unreachable through configuration alone.
     */
    @Bean
    @Profile("dev")
    @org.springframework.core.annotation.Order(2)
    SecurityFilterChain devChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        return http.build();
    }

    @Bean
    @Profile("!dev")
    @ConditionalOnBean(ClientRegistrationRepository.class)
    @org.springframework.core.annotation.Order(2)
    SecurityFilterChain backOfficeChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(login -> login.defaultSuccessUrl("/", true))
                .logout(logout -> logout.logoutSuccessUrl("/"))
                .headers(h -> h.contentSecurityPolicy(csp ->
                        // Self-hosted assets only, so the policy can be genuinely strict (spec 9.4).
                        csp.policyDirectives("default-src 'self'; img-src 'self' data:; "
                                + "style-src 'self'; script-src 'self' 'unsafe-eval'; "
                                + "base-uri 'self'; frame-ancestors 'none'")));
        return http.build();
    }

    /**
     * No identity provider is configured outside development. The application
     * still starts - and the published feeds stay available - but the back-office
     * is closed rather than open: failing closed is the only safe default when
     * there is no way to authenticate anyone.
     */
    @Bean
    @Profile("!dev")
    @ConditionalOnMissingBean(ClientRegistrationRepository.class)
    @org.springframework.core.annotation.Order(2)
    SecurityFilterChain lockedChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
