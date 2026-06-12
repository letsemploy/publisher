package io.letsemploy.publisher.config;

import io.letsemploy.publisher.service.OidcUserProvisioningService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "publisher.security.enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain insecureFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/public/**"));
        return http.build();
    }

    @Bean
    @Order(0)
    @ConditionalOnProperty(name = "publisher.security.enabled", havingValue = "true")
    @ConditionalOnBean(ClientRegistrationRepository.class)
    SecurityFilterChain oidcFilterChain(HttpSecurity http, OidcUserProvisioningService provisioningService) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/public/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(login -> login.successHandler((request, response, authentication) -> {
                    if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
                        provisioningService.provision(oidcUser);
                    }
                    response.sendRedirect("/");
                }))
                .logout(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/public/**"));
        return http.build();
    }
}
