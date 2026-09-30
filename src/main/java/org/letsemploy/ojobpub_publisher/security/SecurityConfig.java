package org.letsemploy.ojobpub_publisher.security;

import org.letsemploy.ojobpub_publisher.account.LocalAccountProperties;
import org.letsemploy.ojobpub_publisher.account.LocalAccountRepo;
import org.letsemploy.ojobpub_publisher.account.LocalAuthenticationProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * The filter chains of spec 9.4, in order: the management API (bearer token),
 * the public feed (anonymous), and the back-office - which is sign-in through
 * identity providers, local accounts or both, the development bypass under the
 * dev profile, or a closed chain when there is no way to sign in at all.
 */
@Configuration
@EnableConfigurationProperties(LocalAccountProperties.class)
public class SecurityConfig {

    /** The account screens a signed-out visitor must reach (spec 7.24). */
    private static final String[] ACCOUNT = {
            "/register", "/register/**", "/password/forgot", "/password/reset", "/password/sent"
    };

    private static final String[] PUBLIC = {
            "/ojobpub/**", "/actuator/health/**", "/actuator/info",
            "/css/**", "/js/**", "/vendor/**", "/images/**", "/favicon.ico", "/error"
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

    /**
     * The back-office outside development: sign-in if an identity provider is
     * configured or local accounts are enabled (spec 2.12), and closed if neither.
     *
     * <p>One bean deciding at creation time, not two behind
     * {@code @ConditionalOnBean}/{@code @ConditionalOnMissingBean}. Those
     * conditions are evaluated while this class is parsed - before
     * auto-configuration has registered the {@link ClientRegistrationRepository}
     * built from {@code spring.security.oauth2.client.*} - so the closed chain won
     * even with a provider configured, and nobody could ever sign in. By the time
     * this method runs every bean definition exists. {@code OidcLoginTest} guards
     * it.
     */
    @Bean
    @Profile("!dev")
    @org.springframework.core.annotation.Order(2)
    SecurityFilterChain backOfficeChain(HttpSecurity http,
                                        ObjectProvider<ClientRegistrationRepository> registrations,
                                        CurrentUserService currentUserService,
                                        LocalAccountProperties localAccounts,
                                        LocalAccountRepo accounts,
                                        PasswordEncoder passwordEncoder)
            throws Exception {
        ClientRegistrationRepository repository = registrations.getIfAvailable();
        LocalAuthenticationProvider local = localAccounts.enabled()
                ? new LocalAuthenticationProvider(accounts, passwordEncoder, localAccounts)
                : null;
        return repository == null && local == null
                ? locked(http)
                : signIn(http, repository, local, currentUserService);
    }

    /**
     * Sign-in: OIDC authorization-code login with PKCE for the configured
     * providers (spec 2.2), a form for local accounts (spec 2.12), or both on one
     * page (spec 7.19).
     *
     * <p>Spring adds PKCE by itself only for a public client. This one is
     * confidential - it holds a client secret - and the spec requires PKCE all the
     * same, because it also protects the code in transit, which the secret does
     * not. Logout ends the provider's session too where the provider advertises
     * an end-session endpoint, and falls back to a local logout where it does not.
     */
    private SecurityFilterChain signIn(HttpSecurity http, ClientRegistrationRepository registrations,
                                       LocalAuthenticationProvider local,
                                       CurrentUserService currentUserService)
            throws Exception {
        // Signing out ends on our sign-in page, saying so (spec 7.19) - via the
        // provider when it advertises an end-session endpoint, directly when not.
        LogoutSuccessHandler logoutSuccess;
        if (registrations != null) {
            // Refuse a provider that would sign people in as nobody (spec 2.2) - at
            // startup, rather than as a login that completes and then goes nowhere.
            LoginOptions.requireSupportedProviders(registrations);

            DefaultOAuth2AuthorizationRequestResolver authorizationRequests =
                    new DefaultOAuth2AuthorizationRequestResolver(registrations,
                            OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
            authorizationRequests.setAuthorizationRequestCustomizer(
                    OAuth2AuthorizationRequestCustomizers.withPkce());

            OidcClientInitiatedLogoutSuccessHandler providerLogout =
                    new OidcClientInitiatedLogoutSuccessHandler(registrations);
            providerLogout.setPostLogoutRedirectUri("{baseUrl}/login?logout");
            providerLogout.setDefaultTargetUrl("/login?logout");
            logoutSuccess = providerLogout;

            // Our own sign-in page rather than Spring's generated one, which is
            // English only and unstyled under this CSP (spec 7.19). It is also
            // where a failed sign-in lands: /login?error.
            http.oauth2Login(login -> login
                    .loginPage("/login")
                    .authorizationEndpoint(a -> a.authorizationRequestResolver(authorizationRequests))
                    // Used only for providers without OpenID Connect - which, after
                    // the check above, means GitHub (spec 2.2).
                    .userInfoEndpoint(u -> u.userService(new GitHubUserService()))
                    .defaultSuccessUrl("/", true));
        } else {
            SimpleUrlLogoutSuccessHandler plainLogout = new SimpleUrlLogoutSuccessHandler();
            plainLogout.setDefaultTargetUrl("/login?logout");
            logoutSuccess = plainLogout;
        }

        if (local != null) {
            // Email and password on the same page (spec 2.12, 7.19). Every failure
            // lands on the one ?error, whatever the reason.
            http.authenticationProvider(local)
                    .formLogin(form -> form
                            .loginPage("/login")
                            .loginProcessingUrl("/login")
                            .usernameParameter("email")
                            .failureUrl("/login?error")
                            .defaultSuccessUrl("/", true));
        }

        http.authorizeHttpRequests(a -> {
                    a.requestMatchers(PUBLIC).permitAll()
                            // By path, so every variant is reachable - ?error, ?logout,
                            // ?lang=de. The configurer's own permitAll() matches the
                            // login and failure URLs exactly, query string included, and
                            // bounced /login?logout back to a bare /login.
                            .requestMatchers("/login").permitAll();
                    if (local != null) {
                        a.requestMatchers(ACCOUNT).permitAll();
                    }
                    a.anyRequest().authenticated();
                })
                .logout(logout -> logout.logoutSuccessHandler(logoutSuccess))
                // After the session's authentication is restored and before anything
                // is authorised: a suspended account goes no further (spec 2.11).
                .addFilterAfter(new SuspendedAccountFilter(currentUserService),
                        org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
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
    private SecurityFilterChain locked(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
