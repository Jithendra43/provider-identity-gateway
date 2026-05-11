package chit.tefca.ingress.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import chit.tefca.ingress.config.AdminProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin authentication endpoints. Mints a JWT via the configured token issuer
 * (mock-jwt in dev) and stores it in an httpOnly cookie for the SPA. The same
 * token can also be returned in the response body for direct API testing.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminProperties properties;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body,
                                                     HttpServletResponse response) {
        // OIDC mode is the production default: authentication happens via the
        // Cognito Hosted UI through Spring Security's oauth2Login() flow at
        // /oauth2/authorization/cognito. The form-based mock login below is
        // retained ONLY when tefca.admin.auth-mode=mock (non-prod profiles).
        if ("mock".equalsIgnoreCase(properties.getAuthMode())) {
            return mockLogin(body, response);
        }
        return ResponseEntity.status(HttpStatus.GONE)
                .body(Map.of(
                    "error", "form_login_disabled",
                    "message", "Admin sign-in uses Cognito SSO. Navigate to /admin/ to be redirected to the hosted login page."
                ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletResponse response) {
        // Hardened logout cookie: HttpOnly + Secure + SameSite=Strict, MaxAge=0.
        ResponseCookie cookie = ResponseCookie.from(properties.getCookieName(), "")
                .path("/")
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite(properties.getCookieSameSite())
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of("status", "logged_out"));
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<Map<String, Object>>> accounts() {
        if (!"mock".equalsIgnoreCase(properties.getAuthMode())
                || properties.getOperators() == null
                || properties.getOperators().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (AdminProperties.Operator op : properties.getOperators()) {
            Map<String, Object> m = new HashMap<>();
            m.put("username", op.getUsername());
            m.put("orgId", op.getOrgId());
            m.put("nodeId", op.getNodeId());
            m.put("roles", op.getRoles());
            m.put("displayName", op.getDisplayName());
            out.add(m);
        }
        out.sort((a, b) -> String.valueOf(a.get("username")).compareTo(String.valueOf(b.get("username"))));
        return ResponseEntity.ok(out);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "not_authenticated"));
        }

        // OIDC (production) — Cognito session via oauth2Login()
        if (auth instanceof OAuth2AuthenticationToken oauth) {
            Object principal = oauth.getPrincipal();
            if (principal instanceof OidcUser oidc) {
                Map<String, Object> claims = new HashMap<>();
                claims.put("subject", oidc.getName());
                claims.put("email", oidc.getEmail());
                claims.put("orgId", oidc.getClaimAsString("custom:org_id"));
                claims.put("nodeId", oidc.getClaimAsString("custom:node_id"));
                List<String> roles = oidc.getClaimAsStringList("cognito:groups");
                if (roles == null || roles.isEmpty()) {
                    roles = List.of(properties.getRequiredRole());
                }
                claims.put("roles", roles);
                claims.put("authorities",
                    auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
                claims.put("exp", oidc.getIdToken().getExpiresAt());
                claims.put("authMode", "oidc");
                return ResponseEntity.ok(claims);
            }
        }

        // Legacy mock-mode JWT (non-prod) — token in HttpOnly cookie
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            Map<String, Object> claims = new HashMap<>();
            claims.put("subject", jwt.getSubject());
            claims.put("orgId", jwt.getClaimAsString("org_id"));
            claims.put("nodeId", jwt.getClaimAsString("node_id"));
            claims.put("roles", jwt.getClaimAsStringList("roles"));
            claims.put("scope", jwt.getClaimAsString("scope"));
            claims.put("exp", jwt.getExpiresAt());
            claims.put("authMode", "mock");
            return ResponseEntity.ok(claims);
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "not_authenticated"));
    }

    private ResponseEntity<Map<String, Object>> mockLogin(LoginRequest body, HttpServletResponse response) {
        String subject = body.username != null ? body.username : "admin@local";

        // Look up the operator profile if pre-provisioned in tefca.admin.operators.
        AdminProperties.Operator profile = null;
        if (properties.getOperators() != null) {
            for (AdminProperties.Operator op : properties.getOperators()) {
                if (op.getUsername() != null && op.getUsername().equalsIgnoreCase(subject)) {
                    profile = op;
                    break;
                }
            }
        }

        if (profile != null) {
            // Password is required and must match the configured value (constant-time compare).
            String submitted = body.password == null ? "" : body.password;
            String expected = profile.getPassword() == null ? "" : profile.getPassword();
            if (expected.isEmpty() || !constantTimeEquals(submitted, expected)) {
                log.warn("Login rejected for {} (bad password)", subject);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "invalid_credentials"));
            }
        } else {
            // Unknown operator — only accept if no operator catalog is configured at all
            // (preserves the previous open-dev behaviour for ad-hoc testing).
            if (properties.getOperators() != null && !properties.getOperators().isEmpty()) {
                log.warn("Login rejected for {} (unknown operator)", subject);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "invalid_credentials"));
            }
        }
        String roles = firstNonBlank(body.roles,
                profile != null ? profile.getRoles() : null,
                properties.getRequiredRole());
        String orgId = firstNonBlank(body.orgId,
                profile != null ? profile.getOrgId() : null,
                "ORG-QHIN-001");
        String nodeId = firstNonBlank(body.nodeId,
                profile != null ? profile.getNodeId() : null,
                "NODE-CW-001");

        WebClient client = WebClient.builder().baseUrl(properties.getMockTokenUrl()).build();

        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("subject", subject);
            form.add("org_id", orgId);
            form.add("node_id", nodeId);
            form.add("roles", roles);
            form.add("scope", "tefca.admin");

            String responseBody = client.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode json = objectMapper.readTree(responseBody);
            String accessToken = json.path("access_token").asText();
            if (accessToken.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("error", "issuer_returned_no_token"));
            }

            // Hardened session cookie: HttpOnly + Secure + SameSite=Strict.
            // Strict prevents the cookie from being attached on cross-site
            // requests, defending against CSRF and OAuth-style cross-site
            // login forging. Combined with the short cookieMaxAgeSeconds, this
            // matches OWASP ASVS V3 cookie controls.
            ResponseCookie cookie = ResponseCookie.from(properties.getCookieName(), accessToken)
                    .path("/")
                    .httpOnly(true)
                    .secure(properties.isCookieSecure())
                    .sameSite(properties.getCookieSameSite())
                    .maxAge(Duration.ofSeconds(properties.getCookieMaxAgeSeconds()))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

            Map<String, Object> resp = new HashMap<>();
            resp.put("access_token", accessToken);
            resp.put("token_type", "Bearer");
            resp.put("subject", subject);
            resp.put("orgId", orgId);
            resp.put("nodeId", nodeId);
            resp.put("roles", List.of(roles.split(",")));
            return ResponseEntity.ok(resp);
        } catch (WebClientResponseException e) {
            log.warn("Mock token issuer failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "issuer_unreachable", "detail", e.getMessage()));
        } catch (Exception e) {
            log.error("Login failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "login_failed", "detail", e.getMessage()));
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (ab.length != bb.length) return false;
        int diff = 0;
        for (int i = 0; i < ab.length; i++) diff |= ab[i] ^ bb[i];
        return diff == 0;
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String orgId;
        private String nodeId;
        private String roles;
        private String password;
    }
}
