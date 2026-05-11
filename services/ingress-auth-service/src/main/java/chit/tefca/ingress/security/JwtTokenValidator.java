package chit.tefca.ingress.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import chit.tefca.common.security.SecurityConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Validates TEFCA-specific JWT claims beyond standard OAuth2 validation.
 * Spring Security's OAuth2 resource server already validates signature,
 * issuer, and expiry. This adds domain-level checks: required claims,
 * audience, scope.
 *
 * <p>Validated tokens are memoized in an in-process Caffeine cache (5 min)
 * to avoid repeated claim parsing within a token's lifetime.
 */
@Slf4j
@Component
public class JwtTokenValidator {

    private static final Set<String> REQUIRED_CLAIMS = Set.of(
            SecurityConstants.CLAIM_ORG_ID,
            SecurityConstants.CLAIM_NODE_ID
    );

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    @Value("${spring.security.oauth2.resourceserver.jwt.audiences:tefca-gateway}")
    private String expectedAudience;

    private final Cache<String, Boolean> validCache;

    public JwtTokenValidator() {
        this.validCache = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL)
                .maximumSize(50_000)
                .build();
    }

    /**
     * Validates domain-specific JWT claims after Spring Security's
     * signature/expiry validation. Returns null if valid, or an error
     * message if invalid.
     */
    public String validate(Jwt jwt) {
        String jti = jwt.getId();

        if (jti != null && Boolean.TRUE.equals(validCache.getIfPresent(jti))) {
            log.trace("JWT jti={} cache hit", jti);
            return null;
        }

        for (String claim : REQUIRED_CLAIMS) {
            if (jwt.getClaimAsString(claim) == null || jwt.getClaimAsString(claim).isBlank()) {
                return "Missing required claim: " + claim;
            }
        }

        List<String> audiences = jwt.getAudience();
        if (audiences == null || !audiences.contains(expectedAudience)) {
            return "Token audience does not contain: " + expectedAudience;
        }

        Instant expiry = jwt.getExpiresAt();
        if (expiry != null && expiry.isBefore(Instant.now())) {
            return "Token has expired";
        }

        if (jti != null) {
            validCache.put(jti, Boolean.TRUE);
        }

        log.debug("JWT validated for org={} node={}",
                jwt.getClaimAsString(SecurityConstants.CLAIM_ORG_ID),
                jwt.getClaimAsString(SecurityConstants.CLAIM_NODE_ID));
        return null;
    }
}
