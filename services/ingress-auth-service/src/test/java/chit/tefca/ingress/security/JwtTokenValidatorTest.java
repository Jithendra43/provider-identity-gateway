package chit.tefca.ingress.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class JwtTokenValidatorTest {

    @InjectMocks
    private JwtTokenValidator validator;

    @Test
    void validJwt_shouldReturnNull() {
        ReflectionTestUtils.setField(validator, "expectedAudience", "tefca-gateway");
        Jwt jwt = buildJwt("org-1", "node-1", List.of("tefca-gateway"), Instant.now().plusSeconds(600));
        assertThat(validator.validate(jwt)).isNull();
    }

    @Test
    void missingOrgId_shouldReturnError() {
        ReflectionTestUtils.setField(validator, "expectedAudience", "tefca-gateway");
        Jwt jwt = buildJwt(null, "node-1", List.of("tefca-gateway"), Instant.now().plusSeconds(600));
        assertThat(validator.validate(jwt)).contains("org_id");
    }

    @Test
    void missingNodeId_shouldReturnError() {
        ReflectionTestUtils.setField(validator, "expectedAudience", "tefca-gateway");
        Jwt jwt = buildJwt("org-1", null, List.of("tefca-gateway"), Instant.now().plusSeconds(600));
        assertThat(validator.validate(jwt)).contains("node_id");
    }

    @Test
    void wrongAudience_shouldReturnError() {
        ReflectionTestUtils.setField(validator, "expectedAudience", "tefca-gateway");
        Jwt jwt = buildJwt("org-1", "node-1", List.of("other-audience"), Instant.now().plusSeconds(600));
        assertThat(validator.validate(jwt)).contains("audience");
    }

    @Test
    void expiredToken_shouldReturnError() {
        ReflectionTestUtils.setField(validator, "expectedAudience", "tefca-gateway");
        Jwt jwt = buildJwt("org-1", "node-1", List.of("tefca-gateway"), Instant.now().minusSeconds(10));
        assertThat(validator.validate(jwt)).contains("expired");
    }

    private Jwt buildJwt(String orgId, String nodeId, List<String> audiences, Instant expiresAt) {
        Map<String, Object> headers = Map.of("alg", "RS256");
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .headers(h -> h.putAll(headers))
                .issuedAt(Instant.now().minusSeconds(120))
                .expiresAt(expiresAt)
                .audience(audiences)
                .subject("test-subject");
        if (orgId != null) {
            builder.claim("org_id", orgId);
        }
        if (nodeId != null) {
            builder.claim("node_id", nodeId);
        }
        return builder.build();
    }
}
