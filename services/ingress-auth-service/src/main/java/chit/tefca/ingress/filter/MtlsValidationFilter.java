package chit.tefca.ingress.filter;

import chit.tefca.common.security.SecurityConstants;
import chit.tefca.ingress.security.PartnerTrustStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates the client certificate forwarded by the TLS-terminating sidecar
 * (NGINX in compose, AWS ALB in production) in the {@code X-Client-Cert}
 * header.
 *
 * <p>Two enforcement modes:</p>
 * <ul>
 *   <li><b>Permissive</b> (default — {@code tefca.mtls.strict=false}): JWT
 *       remains the primary auth, mTLS is an additional layer when present.
 *       Missing header → request is allowed; invalid cert → 403.</li>
 *   <li><b>Strict</b> ({@code tefca.mtls.strict=true}, used in compose and
 *       prod): every TEFCA partner request MUST present a verified client
 *       cert. Missing header → 401 {@code MTLS_REQUIRED}; invalid cert →
 *       403 {@code UNTRUSTED_CERTIFICATE}.</li>
 * </ul>
 *
 * <p>Defense-in-depth: the filter also inspects {@code X-Client-Verify} which
 * NGINX sets to {@code SUCCESS}/{@code FAILED}. If the sidecar said the
 * handshake was not successful, we reject regardless of trust-store contents.</p>
 */
@Slf4j
@Component
@Order(15)
@RequiredArgsConstructor
public class MtlsValidationFilter extends OncePerRequestFilter {

    private static final String CLIENT_VERIFY_HEADER = "X-Client-Verify";

    private final PartnerTrustStore partnerTrustStore;

    @Value("${tefca.mtls.strict:false}")
    private boolean strict;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // mTLS not required for actuator, docs, admin UI, admin API, or favicon.
        // The admin surface authenticates with a cookie session (OIDC in prod);
        // it lives behind a *separate* TLS-only listener (no client-cert).
        return path.startsWith(SecurityConstants.ACTUATOR_PREFIX)
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/admin")
                || path.startsWith("/api/admin/")
                || path.startsWith("/login/oauth2/")
                || path.startsWith("/oauth2/")
                || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientCert  = chit.tefca.common.security.MtlsHeaderSupport.extractClientCertPem(request);
        String verifyState = request.getHeader(CLIENT_VERIFY_HEADER);

        boolean enforced = partnerTrustStore.isMtlsEnabled() && strict;

        // 1) Sidecar said the handshake failed — refuse, no second chances.
        if (verifyState != null && !verifyState.isBlank() && !"SUCCESS".equalsIgnoreCase(verifyState)) {
            log.warn("Rejecting request — upstream sidecar reported X-Client-Verify={}", verifyState);
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "MTLS_HANDSHAKE_FAILED", "TLS sidecar did not verify the client certificate");
            return;
        }

        // 2) Strict mode + no cert at all = misconfigured ingress edge.
        if (enforced && (clientCert == null || clientCert.isBlank())) {
            log.warn("Rejecting request — strict mTLS enabled but no X-Client-Cert header present");
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "MTLS_REQUIRED", "Client certificate required for TEFCA traffic");
            return;
        }

        // 3) Cert present — validate against the trust store.
        if (clientCert != null && !clientCert.isBlank()) {
            if (!partnerTrustStore.isTrustedCertificate(clientCert)) {
                log.warn("Untrusted client certificate received");
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        "UNTRUSTED_CERTIFICATE", "Client certificate is not recognized");
                return;
            }
            log.debug("mTLS validated — thumbprint {}", partnerTrustStore.extractThumbprint(clientCert));
        }

        filterChain.doFilter(request, response);
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
