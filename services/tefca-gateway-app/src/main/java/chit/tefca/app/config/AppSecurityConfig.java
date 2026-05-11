package chit.tefca.app.config;

import chit.tefca.common.security.HmacVerificationFilter;
import chit.tefca.common.security.SecurityConstants;
import chit.tefca.ingress.config.AdminProperties;
import chit.tefca.ingress.filter.JwtAuthenticationFilter;
import chit.tefca.ingress.filter.MtlsValidationFilter;
import chit.tefca.ingress.filter.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Consolidated security configuration for the single-deployable
 * {@code tefca-gateway-app}. Lives in BOOT-INF/classes (not a nested jar),
 * which guarantees component-scan picks it up reliably.
 *
 * <p>Three filter chains, each with explicit {@code securityMatcher}:
 * <ul>
 *   <li>{@code policySecurityFilterChain} (Order 10) — HMAC verify on
 *       {@code /api/v1/policy/**}</li>
 *   <li>{@code routingSecurityFilterChain} (Order 11) — HMAC verify on
 *       {@code /api/v1/routing/**}</li>
 *   <li>{@code adminSecurityFilterChain} (Order 100, catch-all) — admin SPA,
 *       cookie-session JWT auth, public actuator/health and Swagger.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AdminProperties.class)
public class AppSecurityConfig {

    @Value("${tefca.hmac.secret:}")
    private String hmacSecret;

    @Bean("policyHmacFilter")
    public HmacVerificationFilter policyHmacFilter() {
        return new HmacVerificationFilter(hmacSecret, List.of("/api/v1/policy"));
    }

    @Bean("routingHmacFilter")
    public HmacVerificationFilter routingHmacFilter() {
        return new HmacVerificationFilter(hmacSecret, List.of("/api/v1/routing"));
    }

    @Bean("adminApiHmacFilter")
    public HmacVerificationFilter adminApiHmacFilter(AdminProperties adminProperties) {
        String adminRole = "ROLE_" + adminProperties.getRequiredRole();
        return new HmacVerificationFilter(
                hmacSecret,
                List.of("/api/v1/admin"),
                List.of(new SimpleGrantedAuthority(adminRole))
        );
    }

    @Bean("adminApiSecurityFilterChain")
    @Order(9)
    public SecurityFilterChain adminApiSecurityFilterChain(HttpSecurity http,
                                                          AdminProperties adminProperties) throws Exception {
        http
            .securityMatcher("/api/v1/admin/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())
            .addFilterBefore(adminApiHmacFilter(adminProperties), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    @Bean("policySecurityFilterChain")
    @Order(10)
    public SecurityFilterChain policySecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/policy/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())
            .addFilterBefore(policyHmacFilter(), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    @Bean("routingSecurityFilterChain")
    @Order(11)
    public SecurityFilterChain routingSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/routing/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())
            .addFilterBefore(routingHmacFilter(), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    @Bean("directorySecurityFilterChain")
    @Order(12)
    public SecurityFilterChain directorySecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/directory/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Loopback chain for the {@code /mock-pa/**} downstream test endpoints.
     * The {@link chit.tefca.app.mock.MockPaController} validates the
     * gateway-minted internal JWT itself, so this chain disables Spring's
     * oauth2 resource server (which would otherwise try to verify the
     * internal-issuer token against the partner-token JWKS and 401).
     */
    @Bean("mockPaSecurityFilterChain")
    @Order(13)
    public SecurityFilterChain mockPaSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/mock-pa/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())
            .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    @Bean("adminSecurityFilterChain")
    @Order(100)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http,
                                                        MtlsValidationFilter mtlsValidationFilter,
                                                        chit.tefca.ingress.filter.MtlsOrgIdentityFilter mtlsOrgIdentityFilter,
                                                        JwtAuthenticationFilter jwtAuthenticationFilter,
                                                        RateLimitFilter rateLimitFilter,
                                                        AdminProperties adminProperties,
                                                        org.springframework.beans.factory.ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider) throws Exception {
        String adminRole = "ROLE_" + adminProperties.getRequiredRole();
        ClientRegistrationRepository clientRegistrationRepository = clientRegistrationRepositoryProvider.getIfAvailable();
        boolean cognitoEnabled = clientRegistrationRepository != null
                && clientRegistrationRepository.findByRegistrationId("cognito") != null;

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(csrfIgnore())
            )
            // Allow HTTP session ONLY when needed (OIDC oauth2Login keeps the
            // OAuth2AuthenticationToken in HttpSession). Pure API/JWT calls
            // remain effectively stateless because no session is created
            // unless the user goes through the Cognito redirect flow.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .headers(h -> h
                .contentTypeOptions(opt -> {})
                .frameOptions(fr -> fr.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
            )
            .authorizeHttpRequests(auth -> auth
                // PathPatternParser's /** does NOT match the bare path without a trailing
                // segment. Explicitly add the bare /actuator/health path so that the
                // dashboard's health-check calls (which hit /actuator/health with no
                // trailing slash) are permitted without authentication. Without this,
                // the bare path falls through to /actuator/** → authenticated() and the
                // anonymous loopback request gets 401, which the SPA misinterprets as a
                // session expiry and redirects the user to the welcome/login page.
                .requestMatchers(
                    SecurityConstants.ACTUATOR_PREFIX + "/health",
                    SecurityConstants.ACTUATOR_PREFIX + "/health/**").permitAll()
                .requestMatchers(SecurityConstants.ACTUATOR_PREFIX + "/info").permitAll()
                .requestMatchers(SecurityConstants.ACTUATOR_PREFIX + "/prometheus").permitAll()
                .requestMatchers(SecurityConstants.ACTUATOR_PREFIX + "/**").authenticated()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Spring Security OAuth2 client endpoints — Cognito redirect
                // round-trip MUST be public so the unauthenticated bounce
                // through the Hosted UI can complete.
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                // Public landing page that triggers the OIDC redirect.
                .requestMatchers("/", "/error").permitAll()
                // Legacy mock-mode endpoints kept only for non-prod profiles
                // where OIDC is not active. In prod they exist but require
                // a session (they will simply 401 since OIDC is the only
                // way to obtain one).
                .requestMatchers("/api/admin/auth/logout", "/api/admin/auth/accounts").permitAll()
                .requestMatchers("/api/admin/auth/me").authenticated()
                // Public C-HIT welcome / landing page (renders the
                // "Sign in securely" call-to-action that triggers Cognito).
                // Static assets (Next.js chunks, logo) it depends on must
                // also be public so the page can render before sign-in.
                .requestMatchers("/admin/welcome", "/admin/welcome/", "/admin/welcome/**").permitAll()
                // Legacy mock-mode login form. Reachable but inert in OIDC
                // production (it POSTs to /api/admin/auth/login which 401s
                // when Cognito is enabled). Marked permitAll so it never
                // 403s and creates a redirect loop with the SPA AuthProvider.
                .requestMatchers("/admin/login", "/admin/login/", "/admin/login/**").permitAll()
                .requestMatchers("/admin/_next/**").permitAll()
                .requestMatchers("/admin/chit-logo.png", "/admin/favicon.ico", "/admin/logo.png").permitAll()
                // Static admin SPA assets — require a Cognito session.
                // Anonymous hits are bounced to /admin/welcome/ by the
                // oauth2Login() entry point below, where the user clicks
                // "Sign in securely" to start the OIDC round-trip.
                .requestMatchers("/admin", "/admin/", "/admin/**").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/admin/proxy/**").authenticated()
                .requestMatchers("/api/admin/proxy/**").hasAuthority(adminRole)
                .requestMatchers(SecurityConstants.TEFCA_PREFIX + "/**").authenticated()
                // Prior Authorization: inbound auth is mTLS + per-request
                // org mapping enforced by MtlsValidationFilter (@Order 15)
                // and MtlsOrgIdentityFilter (@Order 16). No bearer JWT is
                // required from the partner; the gateway mints its own
                // 60s internal JWT for the downstream call.
                .requestMatchers("/api/v1/pa/**").permitAll()
                // Mock PA service used by integration tests / smoke checks.
                // Validates the gateway-minted internal JWT itself.
                .requestMatchers("/mock-pa/**").permitAll()
                .requestMatchers(SecurityConstants.ADMIN_PREFIX + "/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        if (cognitoEnabled) {
            String requiredRole = adminProperties.getRequiredRole();
            http.oauth2Login(o -> o
                .userInfoEndpoint(u -> u.oidcUserService(cognitoOidcUserService(requiredRole)))
                .defaultSuccessUrl("/admin/dashboard/", true)
                .failureUrl("/admin/welcome/?login_error=true")
            );
            http.logout(l -> l
                .logoutSuccessHandler(cognitoLogoutSuccessHandler(clientRegistrationRepository))
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", adminProperties.getCookieName())
                .permitAll()
            );
            // Browser requests to /admin/** must be redirected to the
            // public C-HIT welcome page (which then offers a "Sign in
            // securely" button that triggers the Cognito Hosted UI). API
            // / JSON requests still get the resource-server's bearer
            // 401 challenge so Postman-style clients see a proper error.
            org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint oauthEntryPoint =
                new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint(
                    "/admin/welcome/");
            http.exceptionHandling(eh -> eh
                .defaultAuthenticationEntryPointFor(oauthEntryPoint,
                    new org.springframework.security.web.util.matcher.MediaTypeRequestMatcher(
                        org.springframework.http.MediaType.TEXT_HTML))
                .defaultAuthenticationEntryPointFor(oauthEntryPoint,
                    new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/admin/**"))
                .defaultAuthenticationEntryPointFor(oauthEntryPoint,
                    new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/"))
            );
        } else {
            // No OIDC client configured (e.g. local dev without Cognito):
            // disable Spring's default form-login and let the legacy
            // /api/admin/auth/login mock-mode flow remain reachable.
            http.logout(l -> l.disable());
        }

        http.addFilterAfter(mtlsValidationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(mtlsOrgIdentityFilter, MtlsValidationFilter.class);
        http.addFilterAfter(jwtAuthenticationFilter, BearerTokenAuthenticationFilter.class);
        http.addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Maps Cognito ID-token claims onto Spring Security authorities.
     *
     * <p>Membership in the admin Cognito user pool is itself the
     * authorization gate (creation is admin-only, MFA is enforced, advanced
     * security is in ENFORCED mode). Therefore every authenticated user is
     * granted {@code ROLE_<requiredRole>} (default {@code ROLE_QHIN_ADMIN}).
     * If the ID token carries a {@code cognito:groups} claim those values
     * are also propagated as {@code ROLE_<group>} so finer-grained
     * authorization can be layered on later by simply assigning users to
     * groups in Cognito.
     */
    private OidcUserService cognitoOidcUserService(String requiredRole) {
        OidcUserService delegate = new OidcUserService();
        return new OidcUserService() {
            @Override
            public OidcUser loadUser(org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest userRequest) {
                OidcUser oidcUser = delegate.loadUser(userRequest);
                Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
                authorities.add(new SimpleGrantedAuthority("ROLE_" + requiredRole));
                List<String> groups = oidcUser.getClaimAsStringList("cognito:groups");
                if (groups != null) {
                    for (String g : groups) {
                        if (g != null && !g.isBlank()) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + g));
                        }
                    }
                }
                return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), "cognito:username");
            }
        };
    }

    private OidcClientInitiatedLogoutSuccessHandler cognitoLogoutSuccessHandler(
            ClientRegistrationRepository registrations) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(registrations);
        // After Cognito clears its session it returns the browser here.
        // {baseUrl} resolves to the public ALB origin (e.g.
        // https://tefca-gw-prod-...elb.amazonaws.com:8444). Hitting this
        // again will retrigger the OIDC redirect, presenting a fresh
        // Hosted UI login if the user wants to sign back in.
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }

    private RequestMatcher csrfIgnore() {
        return new OrRequestMatcher(
                new AntPathRequestMatcher(SecurityConstants.TEFCA_PREFIX + "/**"),
                new AntPathRequestMatcher("/api/v1/pa/**"),
                new AntPathRequestMatcher("/mock-pa/**"),
                new AntPathRequestMatcher("/api/admin/auth/**"),
                new AntPathRequestMatcher("/api/admin/proxy/**"),
                new AntPathRequestMatcher(SecurityConstants.ACTUATOR_PREFIX + "/**"),
                new AntPathRequestMatcher("/oauth2/**"),
                new AntPathRequestMatcher("/mock-fhir/**")
        );
    }

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
