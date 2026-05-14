package chit.tefca.ingress.mockidp;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Self-contained mock OAuth2 IdP for end-to-end smoke tests.
 *
 * <p>Mounted on the ingress-auth-service Fargate task itself so a freshly
 * deployed environment can mint a token, present it to the gateway, and
 * receive a routed FHIR response — no external IdP required.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  {@code /oauth2/.well-known/jwks.json} — public RSA JWKS
 *       (always on — also used by the internal PA hop verifier)</li>
 *   <li>POST {@code /oauth2/token}                — issues a signed JWT.
 *       Gated by {@code tefca.test-idp.token-endpoint-enabled} (default
 *       {@code true}). Set to {@code false} in real prod to lock down the
 *       mint endpoint while still publishing JWKS for downstream PA
 *       verification.</li>
 * </ul>
 *
 * <p>The minted token's {@code iss} / {@code aud} claims must match the
 * gateway's resource-server config ({@code OAUTH2_ISSUER_URI} /
 * {@code OAUTH2_AUDIENCE}) for the token to validate, so both are
 * configurable via {@code tefca.test-idp.issuer-uri} and
 * {@code tefca.test-idp.audience}.
 */
@Slf4j
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class MockIdpController {

    static final String KEY_ID = MockIdpKeyConfig.KEY_ID;

    private final RSAKey rsaJwk;
    private RSASSASigner signer;

    @Value("${tefca.test-idp.issuer-uri:${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:8080}}")
    private String issuer;

    @Value("${tefca.test-idp.audience:${spring.security.oauth2.resourceserver.jwt.audiences:tefca-gateway}}")
    private String audience;

    @Value("${tefca.test-idp.token-endpoint-enabled:true}")
    private boolean tokenEndpointEnabled;

    @PostConstruct
    void init() throws Exception {
        this.signer = new RSASSASigner(rsaJwk);
        log.info("MockIdpController initialised — issuer={} aud={} kid={} tokenEndpointEnabled={}",
                issuer, audience, KEY_ID, tokenEndpointEnabled);
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return new JWKSet(rsaJwk.toPublicJWK()).toJSONObject();
    }

    @PostMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(value = "client_id", defaultValue = "mock-partner-client") String clientId,
            @RequestParam(value = "scope", defaultValue = "system/*.read") String scope,
            @RequestParam(value = "org_id", defaultValue = "ORG-MOCK-001") String orgId,
            @RequestParam(value = "node_id", defaultValue = "NODE-MOCK-001") String nodeId,
            @RequestParam(value = "roles", defaultValue = "PROVIDER") String roles,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "ttl_seconds", defaultValue = "3600") long ttlSeconds) throws JOSEException {

        if (!tokenEndpointEnabled) {
            log.warn("Mint denied — tefca.test-idp.token-endpoint-enabled=false (org_id={})", orgId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "not_found",
                    "error_description", "test-idp /token endpoint disabled"));
        }
        long ttl = Math.min(Math.max(ttlSeconds, 60L), 86400L);
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttl);
        String sub = (subject == null || subject.isBlank()) ? clientId : subject;
        List<String> roleList = Arrays.stream(roles.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(sub)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .claim("scope", scope)
                .claim("org_id", orgId)
                .claim("node_id", nodeId)
                .claim("roles", roleList)
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).type(null).build(),
                claims);
        jwt.sign(signer);

        return ResponseEntity.ok(Map.of(
                "access_token", jwt.serialize(),
                "token_type", "Bearer",
                "expires_in", ttl,
                "scope", scope,
                "org_id", orgId,
                "node_id", nodeId));
    }
}
