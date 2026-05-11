package chit.tefca.ingress.filter;

import chit.tefca.ingress.config.RateLimitConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimitConfig rateLimitConfig;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private RateLimitFilter filter;

    @Test
    void withinLimit_shouldPassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tefca/patient-discovery");
        MockHttpServletResponse response = new MockHttpServletResponse();

        setJwtAuth("org-1");
        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
                .build();
        when(rateLimitConfig.resolveBucket("org-1")).thenReturn(bucket);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        SecurityContextHolder.clearContext();
    }

    @Test
    void exceedsLimit_shouldReturn429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tefca/patient-discovery");
        MockHttpServletResponse response = new MockHttpServletResponse();

        setJwtAuth("org-2");
        // bucket with 0 capacity
        Bucket bucket = Bucket.builder()
                .addLimit(Bandwidth.simple(1, Duration.ofMinutes(1)))
                .build();
        bucket.tryConsume(1); // exhaust
        when(rateLimitConfig.resolveBucket("org-2")).thenReturn(bucket);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
        verify(filterChain, never()).doFilter(request, response);
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuth_shouldPassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/tefca/patient-discovery");
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContextHolder.clearContext();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    private void setJwtAuth(String orgId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .headers(h -> h.put("alg", "RS256"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .audience(List.of("tefca-gateway"))
                .subject("test")
                .claim("org_id", orgId)
                .claim("node_id", "node-1")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
