package chit.tefca.ingress.filter;

import chit.tefca.common.security.SecurityConstants;
import chit.tefca.ingress.config.RateLimitConfig;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-organization rate limiter using Bucket4j.
 * Resolves the org ID from the authenticated JWT claims and applies
 * a token-bucket rate limit per organization.
 */
@Slf4j
@Component
@Order(25)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitConfig rateLimitConfig;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith(SecurityConstants.ACTUATOR_PREFIX)
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/admin")
                || path.startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String orgId = resolveOrgId();

        if (orgId != null) {
            Bucket bucket = rateLimitConfig.resolveBucket(orgId);
            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded for org: {}", orgId);
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests for organization\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveOrgId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return jwt.getClaimAsString(SecurityConstants.CLAIM_ORG_ID);
        }
        return null;
    }
}
