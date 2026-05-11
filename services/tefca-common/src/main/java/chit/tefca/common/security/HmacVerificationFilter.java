package chit.tefca.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Verifies the {@code X-TEFCA-Signature} HMAC on inbound internal requests.
 * Used by policy-service and routing-service to authenticate calls from the
 * ingress-auth-service. When {@code secret} is blank verification is skipped
 * (test/dev mode) so unit tests and local stacks without a shared secret still
 * function.
 *
 * <p>Wraps the request in {@link ContentCachingRequestWrapper} so the body can
 * be hashed for verification AND read again by the controller.
 *
 * <p>Path matching: only paths under {@code protectedPathPrefixes} are checked.
 * Public health/actuator endpoints are skipped.
 */
public class HmacVerificationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HmacVerificationFilter.class);
    private static final long DEFAULT_MAX_SKEW_SECONDS = 300; // 5 minutes

    private final String secret;
    private final long maxSkewSeconds;
    private final List<String> protectedPathPrefixes;
    private final Collection<? extends GrantedAuthority> grantedAuthorities;

    public HmacVerificationFilter(String secret, List<String> protectedPathPrefixes) {
        this(secret, DEFAULT_MAX_SKEW_SECONDS, protectedPathPrefixes, Collections.emptyList());
    }

    public HmacVerificationFilter(String secret, long maxSkewSeconds,
                                  List<String> protectedPathPrefixes) {
        this(secret, maxSkewSeconds, protectedPathPrefixes, Collections.emptyList());
    }

    public HmacVerificationFilter(String secret, List<String> protectedPathPrefixes,
                                  Collection<? extends GrantedAuthority> grantedAuthorities) {
        this(secret, DEFAULT_MAX_SKEW_SECONDS, protectedPathPrefixes, grantedAuthorities);
    }

    public HmacVerificationFilter(String secret, long maxSkewSeconds,
                                  List<String> protectedPathPrefixes,
                                  Collection<? extends GrantedAuthority> grantedAuthorities) {
        this.secret = secret;
        this.maxSkewSeconds = maxSkewSeconds;
        this.protectedPathPrefixes = protectedPathPrefixes;
        this.grantedAuthorities = grantedAuthorities == null ? Collections.emptyList() : grantedAuthorities;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Skip verification entirely if no shared secret is configured.
        if (secret == null || secret.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (!isProtected(path)) {
            chain.doFilter(request, response);
            return;
        }

        String signature = request.getHeader(HmacRequestSigner.HEADER_SIGNATURE);
        String timestamp = request.getHeader(HmacRequestSigner.HEADER_TIMESTAMP);
        if (signature == null || timestamp == null) {
            log.warn("Rejecting request to {} — missing HMAC headers", path);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing HMAC signature");
            return;
        }

        ReplayableRequest wrapped = new ReplayableRequest(request);
        String body = new String(wrapped.cachedBody, StandardCharsets.UTF_8);

        boolean valid;
        try {
            valid = HmacRequestSigner.verify(secret, signature, timestamp, path, body, maxSkewSeconds);
        } catch (NumberFormatException e) {
            log.warn("Rejecting request to {} — malformed HMAC timestamp: {}", path, timestamp);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid HMAC timestamp");
            return;
        }

        if (!valid) {
            log.warn("Rejecting request to {} — HMAC verification failed", path);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid HMAC signature");
            return;
        }

        Authentication prior = SecurityContextHolder.getContext().getAuthentication();
        boolean noRealAuth = prior == null
                || !prior.isAuthenticated()
                || prior instanceof AnonymousAuthenticationToken;
        boolean authInjected = false;
        if (noRealAuth && !grantedAuthorities.isEmpty()) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "internal-hmac",
                    null,
                    grantedAuthorities
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
            authInjected = true;
            log.debug("HMAC verified for {} — injected authority {}", path, grantedAuthorities);
        }

        try {
            chain.doFilter(wrapped, response);
        } finally {
            if (authInjected) {
                SecurityContextHolder.getContext().setAuthentication(prior);
            }
        }
    }

    private boolean isProtected(String path) {
        if (protectedPathPrefixes == null || protectedPathPrefixes.isEmpty()) {
            return false;
        }
        for (String prefix : protectedPathPrefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Request wrapper that buffers the body so it can be read once for HMAC
     * verification and again by the downstream controller.
     */
    private static final class ReplayableRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        ReplayableRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return bais.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { /* no-op */ }
                @Override public int read() { return bais.read(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            Charset cs = getCharacterEncoding() != null
                    ? Charset.forName(getCharacterEncoding()) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cachedBody), cs));
        }

        @Override
        public int getContentLength() {
            return cachedBody.length;
        }

        @Override
        public long getContentLengthLong() {
            return cachedBody.length;
        }
    }
}
