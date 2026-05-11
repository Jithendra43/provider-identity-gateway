package chit.tefca.ingress.filter;

import chit.tefca.common.security.SecurityConstants;
import chit.tefca.ingress.config.AdminProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

/**
 * If a request hitting /api/admin/** carries the admin session cookie but no
 * Authorization header, this filter copies the cookie value into the
 * Authorization header so the downstream OAuth2 resource-server filter can
 * validate it like a normal Bearer token.
 *
 * Runs very early in the filter chain (before BearerTokenAuthenticationFilter).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AdminCookieAuthFilter extends OncePerRequestFilter {

    private final AdminProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Inject the admin session cookie as a Bearer token for any UI-initiated
        // request: the admin REST API and the TEFCA endpoints exercised from
        // the in-app Test Console. We deliberately do NOT inject for the
        // static admin SPA assets (/admin/**) — those are permitAll and a
        // stale cookie should never be able to 401 the login page itself.
        return !(path.startsWith("/api/admin/")
                || path.startsWith("/api/v1/tefca/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Login + logout manage the cookie themselves; never re-inject for them.
        String path = request.getRequestURI();
        boolean isAuthEndpoint = path.equals("/api/admin/auth/login")
                || path.equals("/api/admin/auth/logout");

        if (!isAuthEndpoint && request.getHeader(SecurityConstants.AUTHORIZATION_HEADER) == null) {
            String token = extractCookie(request, properties.getCookieName());
            if (token != null && !token.isBlank()) {
                filterChain.doFilter(new HeaderInjector(request, "Bearer " + token), response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String extractCookie(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private static class HeaderInjector extends HttpServletRequestWrapper {
        private final String authValue;

        HeaderInjector(HttpServletRequest req, String authValue) {
            super(req);
            this.authValue = authValue;
        }

        @Override
        public String getHeader(String name) {
            if (SecurityConstants.AUTHORIZATION_HEADER.equalsIgnoreCase(name)) return authValue;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (SecurityConstants.AUTHORIZATION_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singletonList(authValue));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            java.util.List<String> names = Collections.list(super.getHeaderNames());
            if (names.stream().noneMatch(n -> n.equalsIgnoreCase(SecurityConstants.AUTHORIZATION_HEADER))) {
                names.add(SecurityConstants.AUTHORIZATION_HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}
