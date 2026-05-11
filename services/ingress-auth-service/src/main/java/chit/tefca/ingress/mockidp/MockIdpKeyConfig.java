package chit.tefca.ingress.mockidp;

import com.nimbusds.jose.jwk.RSAKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Provisions the singleton RSA-2048 signing key used by:
 *   1. {@link MockIdpController}                     — partner-facing JWTs
 *   2. {@link chit.tefca.ingress.security.InternalTokenIssuer}
 *      — short-lived service-to-service JWTs minted on the gateway as it
 *      forwards Prior Authorization traffic to the downstream CRD/DTR/PAS
 *      services.
 *
 * <p>The key is intentionally <b>ephemeral</b>: it is generated at process
 * start and lives only in memory. There is no on-disk material, no AWS
 * KMS dependency, and rotation is implicit (a new key on every cold start).
 *
 * <p>Both consumers go through the same {@link RSAKey} bean so the public
 * JWKS exposed at {@code /oauth2/.well-known/jwks.json} can be used to
 * verify both kinds of token. This eliminates the need for a second key
 * registry — the downstream services only need to know one JWKS URL.
 */
@Slf4j
@Configuration
public class MockIdpKeyConfig {

    public static final String KEY_ID = "mock-idp-key-1";

    @Bean
    public RSAKey signingKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) kp.getPublic())
                .privateKey((RSAPrivateKey) kp.getPrivate())
                .keyID(KEY_ID)
                .build();
        log.info("MockIdpKeyConfig — generated ephemeral RSA-2048 signing key kid={}", KEY_ID);
        return jwk;
    }
}
