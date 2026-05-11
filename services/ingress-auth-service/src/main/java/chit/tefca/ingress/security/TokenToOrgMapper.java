package chit.tefca.ingress.security;

import chit.tefca.common.model.RequesterIdentity;
import chit.tefca.common.security.JwtClaimsExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Maps validated JWT claims to the TEFCA organization/node identity.
 */
@Component
@RequiredArgsConstructor
public class TokenToOrgMapper {

    private final JwtClaimsExtractor claimsExtractor;

    public RequesterIdentity mapToIdentity(Jwt jwt) {
        return RequesterIdentity.builder()
                .subject(jwt.getSubject())
                .orgId(claimsExtractor.extractOrgId(jwt))
                .nodeId(claimsExtractor.extractNodeId(jwt))
                .issuer(jwt.getIssuer() != null ? jwt.getIssuer().toString() : null)
                .roles(claimsExtractor.extractRoles(jwt))
                .scopes(jwt.getClaimAsString("scope") != null
                        ? java.util.List.of(jwt.getClaimAsString("scope").split(" "))
                        : java.util.List.of())
                .build();
    }
}
