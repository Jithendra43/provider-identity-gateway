package chit.tefca.ingress.security;

import chit.tefca.common.model.RequesterIdentity;
import chit.tefca.common.security.JwtClaimsExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenToOrgMapperTest {

    @Mock
    private JwtClaimsExtractor claimsExtractor;

    @InjectMocks
    private TokenToOrgMapper mapper;

    @Test
    void mapToIdentity_shouldExtractAllClaims() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .headers(h -> h.put("alg", "RS256"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .subject("sub-1")
                .issuer("https://idp.example.com")
                .claim("org_id", "org-1")
                .claim("node_id", "node-1")
                .claim("scope", "tefca:read tefca:write")
                .build();

        when(claimsExtractor.extractOrgId(jwt)).thenReturn("org-1");
        when(claimsExtractor.extractNodeId(jwt)).thenReturn("node-1");
        when(claimsExtractor.extractRoles(jwt)).thenReturn(List.of("CLINICIAN"));

        RequesterIdentity identity = mapper.mapToIdentity(jwt);

        assertThat(identity.getSubject()).isEqualTo("sub-1");
        assertThat(identity.getOrgId()).isEqualTo("org-1");
        assertThat(identity.getNodeId()).isEqualTo("node-1");
        assertThat(identity.getIssuer()).isEqualTo("https://idp.example.com");
        assertThat(identity.getRoles()).containsExactly("CLINICIAN");
        assertThat(identity.getScopes()).containsExactly("tefca:read", "tefca:write");
    }

    @Test
    void mapToIdentity_withNullIssuerAndScope_shouldHandleGracefully() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .headers(h -> h.put("alg", "RS256"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .subject("sub-2")
                .claim("org_id", "org-2")
                .claim("node_id", "node-2")
                .build();

        when(claimsExtractor.extractOrgId(jwt)).thenReturn("org-2");
        when(claimsExtractor.extractNodeId(jwt)).thenReturn("node-2");
        when(claimsExtractor.extractRoles(jwt)).thenReturn(List.of());

        RequesterIdentity identity = mapper.mapToIdentity(jwt);

        assertThat(identity.getIssuer()).isNull();
        assertThat(identity.getScopes()).isEmpty();
    }
}
