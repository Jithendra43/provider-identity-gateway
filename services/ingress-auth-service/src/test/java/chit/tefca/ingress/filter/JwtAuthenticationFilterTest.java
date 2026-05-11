package chit.tefca.ingress.filter;

import chit.tefca.common.model.RequesterIdentity;
import chit.tefca.ingress.security.JwtReplayCache;
import chit.tefca.ingress.security.JwtTokenValidator;
import chit.tefca.ingress.security.TokenToOrgMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenValidator jwtTokenValidator;

    @Mock
    private TokenToOrgMapper tokenToOrgMapper;

    @Mock
    private JwtReplayCache jwtReplayCache;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @Test
    void validJwt_shouldSetIdentityAttribute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tefca/patient-discovery");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Jwt jwt = buildJwt();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(jwtTokenValidator.validate(jwt)).thenReturn(null);
        when(jwtReplayCache.recordOrDetectReplay(any(), any(), any())).thenReturn(true);
        RequesterIdentity identity = RequesterIdentity.builder().orgId("org-1").nodeId("node-1").build();
        when(tokenToOrgMapper.mapToIdentity(jwt)).thenReturn(identity);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(request.getAttribute(JwtAuthenticationFilter.REQUESTER_IDENTITY_ATTR)).isEqualTo(identity);
        verify(filterChain).doFilter(request, response);

        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidJwtClaims_shouldReturn403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tefca/patient-discovery");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Jwt jwt = buildJwt();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(jwtTokenValidator.validate(jwt)).thenReturn("Missing required claim: org_id");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("INVALID_TOKEN_CLAIMS");
        verify(filterChain, never()).doFilter(request, response);

        SecurityContextHolder.clearContext();
    }

    @Test
    void actuatorPath_shouldNotFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void noAuthentication_shouldPassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tefca/patient-discovery");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContextHolder.clearContext();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    private Jwt buildJwt() {
        return Jwt.withTokenValue("test-token")
                .headers(h -> h.put("alg", "RS256"))
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .audience(List.of("tefca-gateway"))
                .subject("test-subject")
                .claim("org_id", "org-1")
                .claim("node_id", "node-1")
                .build();
    }
}
