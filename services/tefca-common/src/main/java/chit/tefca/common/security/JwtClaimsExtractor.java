package chit.tefca.common.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Extracts TEFCA-specific claims from a validated JWT token.
 */
@Component
public class JwtClaimsExtractor {

    public String extractOrgId(Jwt jwt) {
        return jwt.getClaimAsString(SecurityConstants.CLAIM_ORG_ID);
    }

    public String extractNodeId(Jwt jwt) {
        return jwt.getClaimAsString(SecurityConstants.CLAIM_NODE_ID);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Jwt jwt) {
        Object roles = jwt.getClaim(SecurityConstants.CLAIM_ROLES);
        if (roles instanceof List) {
            return (List<String>) roles;
        }
        return Collections.emptyList();
    }

    public String extractScopes(Jwt jwt) {
        return jwt.getClaimAsString(SecurityConstants.CLAIM_SCOPES);
    }
}
