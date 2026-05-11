package chit.tefca.ingress.config;

import chit.tefca.common.security.SecurityConstants;
import chit.tefca.ingress.filter.JwtAuthenticationFilter;
import chit.tefca.ingress.filter.MtlsValidationFilter;
import chit.tefca.ingress.filter.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
@org.springframework.context.annotation.Profile("!prod")
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AdminProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final MtlsValidationFilter mtlsValidationFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final AdminProperties adminProperties;

    @Bean("ingressSecurityFilterChain")
    @org.springframework.core.annotation.Order(100)
    public SecurityFilterChain ingressSecurityFilterChain(HttpSecurity http) throws Exception {
        String adminRole = "ROLE_" + adminProperties.getRequiredRole();

        http
            // CSRF: disabled for stateless Bearer-token APIs (TEFCA partner
            // traffic) where every request carries a fresh JWT. Enabled for
            // cookie-authenticated admin mutating endpoints (/api/admin/proxy
            // POST/PUT/DELETE) so a malicious site cannot forge state-changing
            // calls against an operator's session. SameSite=Strict on the
            // session cookie is the primary defence; CSRF tokens are
            // belt-and-suspenders.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(csrfIgnore())
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // ALB Cognito (admin :8444) and ALB mTLS (partner :443) are the
            // sole perimeter authentication. Disable Spring's default
            // form-login/basic-auth so the framework never renders its built-in
            // /login HTML page to users who already passed Cognito.
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .headers(headers -> headers
                .contentTypeOptions(opt -> {})
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(SecurityConstants.ACTUATOR_PREFIX + "/health/**").permitAll()
                .requestMatchers(SecurityConstants.ACTUATOR_PREFIX + "/info").permitAll()
                .requestMatchers(SecurityConstants.ACTUATOR_PREFIX + "/prometheus").permitAll()
                // Other actuator endpoints (env, loggers, configprops, beans, mappings, metrics)
                // are restricted to authenticated admin operators so the Configuration page works.
                .requestMatchers(SecurityConstants.ACTUATOR_PREFIX + "/**").authenticated()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // Admin UI static assets and SPA fallback are public; the SPA itself
                // calls /api/admin/auth/login to obtain a session.
                .requestMatchers("/admin", "/admin/", "/admin/**").permitAll()
                .requestMatchers("/api/admin/auth/login", "/api/admin/auth/logout", "/api/admin/auth/accounts").permitAll()
                .requestMatchers("/api/admin/auth/me").authenticated()
                // Read-only proxy GETs require any authenticated operator;
                // mutating verbs require the configured admin role.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/admin/proxy/**").authenticated()
                .requestMatchers("/api/admin/proxy/**").hasAuthority(adminRole)
                .requestMatchers(SecurityConstants.TEFCA_PREFIX + "/**").authenticated()
                .requestMatchers(SecurityConstants.ADMIN_PREFIX + "/**").authenticated()
                // Everything else (root, /admin SPA, static assets) is reachable
                // because the ALB has already enforced Cognito on :8444.
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        http.addFilterAfter(mtlsValidationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(jwtAuthenticationFilter, BearerTokenAuthenticationFilter.class);
        http.addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Paths that legitimately bypass CSRF: stateless TEFCA partner APIs
     * (Bearer-token authenticated, no cookie), public auth bootstrap, and
     * read-only actuator/health.
     */
    private RequestMatcher csrfIgnore() {
        return new OrRequestMatcher(
                new AntPathRequestMatcher(SecurityConstants.TEFCA_PREFIX + "/**"),
                new AntPathRequestMatcher("/api/admin/auth/**"),
                new AntPathRequestMatcher("/api/admin/proxy/**"),
                new AntPathRequestMatcher(SecurityConstants.ACTUATOR_PREFIX + "/**"),
                // Loopback mock IdP / mock FHIR (smoke-test only; safe — no cookie auth)
                new AntPathRequestMatcher("/oauth2/**"),
                new AntPathRequestMatcher("/mock-fhir/**")
        );
    }

    /**
     * Converts the JWT's `roles` claim (a list of strings) into Spring
     * authorities prefixed with ROLE_ so .hasAuthority("ROLE_QHIN_ADMIN")
     * works against the cookie-derived bearer token.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthoritiesClaimName("scope");
        scopes.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter((Jwt jwt) -> {
            Collection<GrantedAuthority> authorities = scopes.convert(jwt);
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                Collection<GrantedAuthority> roleAuths = roles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .collect(Collectors.toList());
                authorities = Stream.concat(
                        authorities == null ? Stream.empty() : authorities.stream(),
                        roleAuths.stream()).collect(Collectors.toList());
            }
            return authorities;
        });
        return converter;
    }
}
