package chit.tefca.ingress.filter;

import chit.tefca.common.security.SecurityConstants;
import chit.tefca.ingress.security.CertificateOrgMapper;
import chit.tefca.ingress.security.PartnerTrustStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Runs <i>after</i> {@link MtlsValidationFilter} (which has already proven
 * the client cert is trusted). This filter:
 *
 * <ol>
 *   <li>Extracts the SHA-256 thumbprint from the validated client cert.</li>
 *   <li>Looks up the owning provider organization id via
 *       {@link CertificateOrgMapper}.</li>
 *   <li>Sets the resolved org id as the {@code tefca.providerOrgId} request
 *       attribute so downstream Prior Authorization controllers can attach
 *       it to every audit event and downstream JWT.</li>
 * </ol>
 *
 * <p>Only applies to {@code /api/v1/pa/**} so it does not perturb the
 * existing TEFCA flows (which use the JWT {@code org_id} claim instead).
 * For non-PA paths the filter is an immediate pass-through.</p>
 *
 * <p>For PA traffic the filter is fail-closed: if the cert thumbprint does
 * not resolve to an active provider, the request is rejected with 403 to
 * prevent any silent fall-back to a default identity. This matches the
 * HIPAA Security Rule §164.308(a)(4) requirement that all access be tied
 * to a uniquely identified workforce member or partner.</p>
 */
@Slf4j
@Component
@Order(16)
@RequiredArgsConstructor
@ConditionalOnProperty(value = "tefca.pa.enabled", havingValue = "true", matchIfMissing = true)
public class MtlsOrgIdentityFilter extends OncePerRequestFilter {

    public static final String PROVIDER_ORG_ID_ATTR = "tefca.providerOrgId";

    private final PartnerTrustStore partnerTrustStore;
    private final CertificateOrgMapper certificateOrgMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/pa/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String certData = chit.tefca.common.security.MtlsHeaderSupport.extractClientCertPem(request);
        if (certData == null || certData.isBlank()) {
            // PA path requires mTLS — MtlsValidationFilter strict mode should already
            // have rejected, but defense-in-depth: refuse here too.
            log.warn("PA request rejected — no client certificate header present");
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "MTLS_REQUIRED", "Client certificate required for PA traffic");
            return;
        }

        String thumbprint;
        try {
            thumbprint = partnerTrustStore.extractThumbprint(certData);
        } catch (Exception e) {
            log.warn("PA request rejected — failed to extract thumbprint: {}", e.getMessage());
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "INVALID_CERTIFICATE", "Could not parse client certificate");
            return;
        }

        Optional<String> orgId = certificateOrgMapper.resolveOrgId(thumbprint);
        if (orgId.isEmpty()) {
            log.warn("PA request rejected — thumbprint {} not mapped to a provider org", thumbprint);
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "PROVIDER_NOT_FOUND", "Client certificate is not linked to an active provider");
            return;
        }

        request.setAttribute(PROVIDER_ORG_ID_ATTR, orgId.get());
        log.debug("PA request — resolved providerOrgId={} from cert thumbprint", orgId.get());
        filterChain.doFilter(request, response);
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
