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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Date;
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
 *   <li>GET  {@code /oauth2/.well-known/jwks.json} — public RSA JWKS</li>
 *   <li>POST {@code /oauth2/token}                — issues a signed JWT</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class MockIdpController {

    static final String ISSUER = "http://localhost:8080";
    static final String AUDIENCE = "tefca-gateway";
    static final String KEY_ID = MockIdpKeyConfig.KEY_ID;

    private final RSAKey rsaJwk;
    private RSASSASigner signer;

    @PostConstruct
    void init() throws Exception {
        this.signer = new RSASSASigner(rsaJwk);
        log.info("MockIdpController initialised — issuer={} kid={}", ISSUER, KEY_ID);
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return new JWKSet(rsaJwk.toPublicJWK()).toJSONObject();
    }

    @PostMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> token(
            @RequestParam(value = "client_id", defaultValue = "mock-partner-client") String clientId,
            @RequestParam(value = "scope", defaultValue = "system/*.read") String scope) throws JOSEException {

        Instant now = Instant.now();
        Instant exp = now.plusSeconds(3600);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(clientId)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .claim("scope", scope)
                .claim("org_id", "ORG-MOCK-001")
                .claim("node_id", "NODE-MOCK-001")
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).type(null).build(),
                claims);
        jwt.sign(signer);

        return Map.of(
                "access_token", jwt.serialize(),
                "token_type", "Bearer",
                "expires_in", 3600,
                "scope", scope);
    }
}
