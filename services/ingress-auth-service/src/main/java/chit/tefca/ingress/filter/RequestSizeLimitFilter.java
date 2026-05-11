package chit.tefca.ingress.filter;

import chit.tefca.common.security.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces maximum request body size to prevent abuse.
 */
@Component
@Order(10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > SecurityConstants.MAX_REQUEST_SIZE_BYTES) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.getWriter().write("{\"error\":\"REQUEST_TOO_LARGE\",\"message\":\"Request body exceeds maximum size\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
