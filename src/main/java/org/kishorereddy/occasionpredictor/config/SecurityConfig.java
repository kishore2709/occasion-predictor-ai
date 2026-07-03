package org.kishorereddy.occasionpredictor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Roles:
 *   ADMIN     — full access (all endpoints + admin operations)
 *   SERVICE   — create and read predictions (machine-to-machine)
 *   REVIEWER  — read predictions and audit data
 *   READ_ONLY — health check only
 *
 * Set app.security.enabled=false (default) for local development without Keycloak.
 * Set JWT_ISSUER_URI env var + app.security.enabled=true to activate JWT validation.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${app.security.enabled:false}")
    private boolean securityEnabled;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!securityEnabled || issuerUri.isBlank()) {
            log.warn("[SECURITY] Security is DISABLED — all endpoints are publicly accessible. "
                    + "Set app.security.enabled=true and JWT_ISSUER_URI for production.");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        log.info("[SECURITY] OAuth2 JWT resource server active. Issuer: {}", issuerUri);

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/occasion/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/occasion/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/occasion/predictions").hasAnyRole("ADMIN", "SERVICE")
                .requestMatchers(HttpMethod.GET, "/api/v1/occasion/predictions/**").hasAnyRole("ADMIN", "SERVICE", "REVIEWER")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(JwtDecoders.fromIssuerLocation(issuerUri))
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * Extracts roles from JWT claims. Supports:
     *   - Keycloak: realm_access.roles → ["ADMIN", "SERVICE", ...]
     *   - Generic:  roles              → ["ADMIN", "SERVICE", ...]
     * Both lists are merged and prefixed with ROLE_.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<String> roles = new ArrayList<>();

        // Keycloak format: realm_access.roles
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> list) {
            list.stream().map(Object::toString).forEach(roles::add);
        }

        // Generic format: roles claim
        List<String> generic = jwt.getClaimAsStringList("roles");
        if (generic != null) roles.addAll(generic);

        return roles.stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                .toList();
    }
}
