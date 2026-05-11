package chit.tefca.ingress.filter;

import chit.tefca.common.correlation.CorrelationIdHolder;
import chit.tefca.common.model.RequesterIdentity;
import chit.tefca.common.security.SecurityConstants;
import chit.tefca.ingress.security.JwtReplayCache;
import chit.tefca.ingress.security.JwtTokenValidator;
import chit.tefca.ingress.security.TokenToOrgMapper;
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
 * Post-authentication filter that validates TEFCA-specific JWT claims 
 * and maps the authenticated principal to a RequesterIdentity stored
 * as a request attribute for downstream use.
 * 
 * Spring Security's OAuth2 resource server handles signature/issuer/expiry validation.
 * This filter adds domain-level claim validation after authentication succeeds.
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String REQUESTER_IDENTITY_ATTR = "tefca.requesterIdentity";

    private final JwtTokenValidator jwtTokenValidator;
    private final TokenToOrgMapper tokenToOrgMapper;
    private final JwtReplayCache jwtReplayCache;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith(SecurityConstants.ACTUATOR_PREFIX) 
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/admin")
                || path.startsWith("/api/v1/pa/")
                || path.startsWith("/mock-pa/")
                || path.equals("/api/admin/auth/login")
                || path.equals("/api/admin/auth/accounts")
                || path.equals("/api/admin/auth/logout");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            
            String validationError = jwtTokenValidator.validate(jwt);
            if (validationError != null) {
                log.warn("JWT claim validation failed: {}", validationError);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"INVALID_TOKEN_CLAIMS\",\"message\":\"" + validationError + "\"}");
                return;
            }

            // TEFCA replay protection: same (jti, correlationId) pair within
            // token lifetime → reject as replay (defense-in-depth on top of
            // mTLS + signature). See TEFCA QHIN Tech Framework, 45 CFR §164.312(c)(1).
            String correlationId = CorrelationIdHolder.get();
            if (!jwtReplayCache.recordOrDetectReplay(jwt.getId(), correlationId, jwt.getExpiresAt())) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"REPLAY_DETECTED\",\"message\":\"Request fingerprint already seen within token TTL\"}");
                return;
            }

            RequesterIdentity identity = tokenToOrgMapper.mapToIdentity(jwt);
            request.setAttribute(REQUESTER_IDENTITY_ATTR, identity);
            log.debug("Authenticated requester: org={} node={}", identity.getOrgId(), identity.getNodeId());
        }

        filterChain.doFilter(request, response);
    }
}
