package chit.tefca.ingress.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import chit.tefca.ingress.mockidp.MockIdpKeyConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Mints short-lived RS256 JWTs that the gateway attaches as
 * {@code Authorization: Bearer …} on outbound calls to the downstream
 * Prior Authorization (CRD/DTR/PAS) services.
 *
 * <p>Each token is unique per request — there is no caching. The token
 * carries the proven mTLS-validated provider organization id ({@code sub}
 * and {@code org_id}), the correlation id, and a fixed
 * {@code exchange_purpose=PRIOR_AUTHORIZATION} claim so downstream services
 * can enforce purpose-of-use without re-running the policy engine.
 *
 * <p>The signing key is the same RSA-2048 ephemeral key the
 * {@link MockIdpController} uses, so the JWKS at
 * {@code /oauth2/.well-known/jwks.json} verifies both partner tokens and
 * internal service tokens. Downstream services distinguish them by
 * inspecting the {@code iss} claim:
 * <ul>
 *   <li>{@code http://localhost:8080}                — partner token</li>
 *   <li>{@code tefca-gateway-internal}               — internal service token</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalTokenIssuer {

    public static final String INTERNAL_ISSUER = "tefca-gateway-internal";
    public static final long DEFAULT_TTL_SECONDS = 60;

    private final RSAKey rsaJwk;
    private RSASSASigner signer;

    @Value("${tefca.internal-jwt.issuer:" + INTERNAL_ISSUER + "}")
    private String issuer;

    @Value("${tefca.internal-jwt.ttl-seconds:60}")
    private long ttlSeconds;

    @PostConstruct
    void init() throws JOSEException {
        this.signer = new RSASSASigner(rsaJwk);
        log.info("InternalTokenIssuer initialised — issuer={} ttl={}s kid={}",
                issuer, ttlSeconds, rsaJwk.getKeyID());
    }

    /**
     * Mint a fresh JWT for a single downstream call.
     *
     * @param audience       service name (e.g. {@code "pa-crd-service"}).
     * @param providerOrgId  proven provider org id from inbound mTLS.
     * @param correlationId  end-to-end correlation id (for downstream audit chaining).
     * @return the serialized compact-form JWT, ready for the {@code Bearer} header.
     */
    public String mintForService(String audience, String providerOrgId, String correlationId) {
        if (signer == null) {
            throw new IllegalStateException("InternalTokenIssuer not initialised");
        }
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience != null ? audience : "downstream")
                .subject(providerOrgId != null ? providerOrgId : "unknown")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
                .claim("org_id", providerOrgId)
                .claim("exchange_purpose", "PRIOR_AUTHORIZATION")
                .claim("correlation_id", correlationId)
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID()).build(),
                claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign internal JWT", e);
        }
        return jwt.serialize();
    }
}
