package chit.tefca.ingress.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds security headers to all responses.
 */
@Component
@Order(5)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "0");
        response.setHeader("Referrer-Policy", "no-referrer");

        String path = request.getRequestURI();
        boolean isAdminUi = path != null && path.startsWith("/admin");

        if (isAdminUi) {
            // Admin UI bundle (Next.js static export) needs to load its own JS/CSS,
            // call the same-origin admin API, and pull Google Fonts. Use a permissive
            // CSP scoped to those needs but still deny inline scripts.
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Content-Security-Policy", String.join("; ",
                    "default-src 'self'",
                    "script-src 'self' 'unsafe-inline'",
                    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
                    "font-src 'self' https://fonts.gstatic.com data:",
                    "img-src 'self' data:",
                    "connect-src 'self'",
                    "frame-ancestors 'none'",
                    "base-uri 'self'",
                    "form-action 'self'"));
        } else {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Content-Security-Policy", "default-src 'none'");
        }

        filterChain.doFilter(request, response);
    }
}
