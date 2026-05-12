package chit.tefca.ingress.controller;

import chit.tefca.common.security.HmacRequestSigner;
import chit.tefca.ingress.config.AdminProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Generic reverse proxy that forwards admin UI requests to an allow-listed
 * downstream service, preserving the operator's Authorization header so that
 * downstream OAuth2 / HMAC checks can run unchanged.
 *
 * Path layout: /api/admin/proxy/{service}/{path...}
 *   service  = key in tefca.admin.proxy.services (e.g. policy, routing, directory)
 *   path...  = path forwarded verbatim to the downstream service
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/proxy")
@RequiredArgsConstructor
public class AdminProxyController {

    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length");

    private static final Set<String> ALLOWED_PATH_PREFIXES = Set.of(
            "/api/v1/admin/", "/api/v1/policy/", "/api/v1/directory/",
            "/api/v1/routing/", "/api/v1/tefca/", "/actuator/");

    private final AdminProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final org.springframework.beans.factory.ObjectProvider<OAuth2AuthorizedClientService>
            authorizedClientServiceProvider;

    @Value("${tefca.hmac.secret:}")
    private String hmacSecret;

    @RequestMapping("/{service}/**")
    public Mono<ResponseEntity<byte[]>> proxy(@org.springframework.web.bind.annotation.PathVariable String service,
                                              HttpServletRequest request,
                                              @org.springframework.web.bind.annotation.RequestBody(required = false) byte[] body) {
        String baseUrl = properties.getProxy().getServices().get(service);
        if (baseUrl == null) {
            return Mono.just(ResponseEntity.status(404)
                    .body(("{\"error\":\"unknown_service\",\"service\":\"" + service + "\"}").getBytes()));
        }

        String fullUri = request.getRequestURI();
        String prefix = "/api/admin/proxy/" + service;
        String downstreamPath = fullUri.length() > prefix.length() ? fullUri.substring(prefix.length()) : "/";
        if (downstreamPath.isEmpty()) {
            downstreamPath = "/";
        }
        if (!isAllowed(downstreamPath)) {
            return Mono.just(ResponseEntity.status(403)
                    .body(("{\"error\":\"path_not_allowlisted\",\"path\":\"" + downstreamPath + "\"}").getBytes()));
        }

        String query = request.getQueryString();
        String targetUrl = baseUrl + downstreamPath + (query != null ? "?" + query : "");
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        log.debug("Admin proxy {} {} -> {}", method, fullUri, targetUrl);

        WebClient client = webClientBuilder.build();
        WebClient.RequestBodySpec spec = client.method(method).uri(targetUrl);

        // Actuator endpoints on the loopback are protected by HMAC + permitAll
        // only. Forwarding the inbound Authorization header (which may have
        // been derived from the legacy admin-cookie by AdminCookieAuthFilter,
        // or be a Cognito-issued token) makes the downstream resource server
        // try to validate it against the partner JWKS and 401 with a Bearer
        // challenge. Strip Authorization (and the Cookie that produced it)
        // for actuator hops and let HMAC alone authenticate the call.
        boolean isActuator = downstreamPath.startsWith("/actuator/");

        // Forward headers (skip hop-by-hop and Host).
        java.util.Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String lower = name.toLowerCase();
            if (HOP_BY_HOP.contains(lower)) continue;
            if (isActuator && ("authorization".equals(lower) || "cookie".equals(lower))) continue;
            spec.header(name, request.getHeader(name));
        }

        // If there is no Authorization header but we DO have an authenticated principal
        // (e.g. cookie-based session), inject the bearer token from the validated JWT.
        // Skipped for /actuator/** — those rely on HMAC only.
        if (!isActuator && request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAuth.getToken().getTokenValue());
            } else if (auth instanceof OAuth2AuthenticationToken oauthAuth) {
                // Cognito SSO session: pull the access token from the
                // OAuth2AuthorizedClientService and forward it. Without this
                // any downstream chain that demands a Bearer (e.g. the
                // partner-token resource server on /api/v1/tefca/**) will
                // 401 the loopback hop and the proxy returns 500.
                OAuth2AuthorizedClientService svc = authorizedClientServiceProvider.getIfAvailable();
                if (svc != null) {
                    OAuth2AuthorizedClient authorizedClient = svc.loadAuthorizedClient(
                            oauthAuth.getAuthorizedClientRegistrationId(), oauthAuth.getName());
                    if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
                        spec.header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + authorizedClient.getAccessToken().getTokenValue());
                    }
                }
            }
        }

        // Sign the request with the shared HMAC secret so HmacVerificationFilter
        // on the downstream service accepts it as an authenticated internal call.
        if (hmacSecret != null && !hmacSecret.isBlank()) {
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String bodyForSig = body != null ? new String(body, StandardCharsets.UTF_8) : "";
            try {
                String signature = HmacRequestSigner.sign(hmacSecret, timestamp, downstreamPath, bodyForSig);
                spec.header(HmacRequestSigner.HEADER_TIMESTAMP, timestamp);
                spec.header(HmacRequestSigner.HEADER_SIGNATURE, signature);
            } catch (Exception e) {
                log.warn("Failed to sign admin proxy request: {}", e.getMessage());
            }
        }

        // Attach the request body after all headers are set. bodyValue() returns
        // RequestHeadersSpec<?>, not RequestBodySpec, so a cast would throw a
        // ClassCastException on any PUT/POST/PATCH with a body.  Use a separate
        // typed variable to avoid the cast entirely.
        WebClient.RequestHeadersSpec<?> headersSpec;
        if (body != null && body.length > 0) {
            String ct = request.getContentType() != null ? request.getContentType() : "application/octet-stream";
            headersSpec = spec.contentType(MediaType.parseMediaType(ct)).bodyValue(body);
        } else {
            headersSpec = spec;
        }

        return headersSpec.exchangeToMono(resp ->
                        resp.bodyToMono(byte[].class)
                                .defaultIfEmpty(new byte[0])
                                .map(b -> {
                                    ResponseEntity.BodyBuilder rb = ResponseEntity.status(resp.statusCode().value());
                                    resp.headers().asHttpHeaders().forEach((k, v) -> {
                                        if (!HOP_BY_HOP.contains(k.toLowerCase())) {
                                            rb.header(k, v.toArray(new String[0]));
                                        }
                                    });
                                    return rb.body(b);
                                }))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.warn("Admin proxy {} {} -> {} returned {}: {}",
                            method, fullUri, targetUrl, ex.getStatusCode(),
                            ex.getResponseBodyAsString());
                    return Mono.just(
                            ResponseEntity.status(ex.getStatusCode().value())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(ex.getResponseBodyAsByteArray()));
                })
                .onErrorResume(Exception.class, ex -> {
                    log.warn("Admin proxy {} {} -> {} failed: {}: {}",
                            method, fullUri, targetUrl,
                            ex.getClass().getSimpleName(), ex.getMessage());
                    Map<String, Object> err = new HashMap<>();
                    err.put("error", "upstream_unreachable");
                    err.put("detail", ex.getMessage());
                    err.put("target", targetUrl);
                    try {
                        byte[] payload = new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsBytes(err);
                        return Mono.just(ResponseEntity.status(502)
                                .contentType(MediaType.APPLICATION_JSON).body(payload));
                    } catch (Exception ignored) {
                        return Mono.just(ResponseEntity.status(502).body(new byte[0]));
                    }
                });
    }

    private boolean isAllowed(String path) {
        for (String prefix : ALLOWED_PATH_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }
}