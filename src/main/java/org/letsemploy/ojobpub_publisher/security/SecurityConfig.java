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

    /**
     * Order 1: the management API. A bearer token per request, no session and no
     * CSRF (spec 11.2). It must not fall through to the back-office chain, or an
     * integration gets a redirect to an identity provider instead of JSON.
     */
    @Bean
    @org.springframework.core.annotation.Order(0)
    SecurityFilterChain managementApiChain(
            HttpSecurity http,
            org.letsemploy.ojobpub_publisher.token.ServiceTokenService tokens,
            org.letsemploy.ojobpub_publisher.api.ApiRateLimiter rateLimiter,
            @org.springframework.beans.factory.annotation.Value("${app.api.max-request-bytes:262144}")
            long maxRequestBytes) throws Exception {
        ServiceTokenAuthFilter tokenFilter = new ServiceTokenAuthFilter(tokens);
        http.securityMatcher(PathPatternRequestMatcher.withDefaults().matcher("/graphql"))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .addFilterBefore(tokenFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                // Credentials first, then the transport budget: an unauthenticated
                // request has no token to charge the request against (spec 11.6).
                .addFilterAfter(
                        new org.letsemploy.ojobpub_publisher.api.ApiTransportFilter(
                                rateLimiter, maxRequestBytes),
                        ServiceTokenAuthFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        (req, res, ex) -> res.sendError(401)));
        return http.build();
    }

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
        // /graphql is matched by the API chain at Order(0), so the bypass never
        // reaches it: the API is token-authenticated even in development.
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
                        // No 'unsafe-inline' and no 'unsafe-eval': all behaviour lives in
                        // a module file, so nothing evaluates code from a string (spec 9.4).
                        csp.policyDirectives("default-src 'self'; img-src 'self' data:; "
                                + "style-src 'self'; script-src 'self'; "
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
