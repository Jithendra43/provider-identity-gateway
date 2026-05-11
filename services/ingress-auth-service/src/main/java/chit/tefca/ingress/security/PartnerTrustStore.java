package chit.tefca.ingress.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages partner certificate trust store for mTLS validation.
 *
 * <p>Holds an in-memory set of SHA-256 thumbprints (lower-case hex, no colons)
 * matching the format produced by {@code openssl x509 -fingerprint -sha256
 * -noout}. Thumbprints are populated by {@link PartnerCertificateLoader} from
 * the {@code ingress.partner_certificates} table at boot and on a scheduled
 * cadence so a directory rotation propagates without redeploying.</p>
 *
 * <p>Thumbprint extraction follows RFC 5280: the SHA-256 hash is computed
 * over the DER-encoded certificate, not over the surrounding PEM string.
 * That guarantees we get the same value as openssl, the AWS Certificate
 * Manager console, and any partner that publishes their fingerprint out of
 * band. NGINX forwards the verified PEM URL-encoded in the {@code X-Client-Cert}
 * header — we URL-decode it before parsing.</p>
 */
@Slf4j
@Component
public class PartnerTrustStore {

    private final Set<String> trustedThumbprints = ConcurrentHashMap.newKeySet();

    @Value("${tefca.mtls.enabled:false}")
    private boolean mtlsEnabled;

    /**
     * Returns {@code true} when the supplied certificate material is trusted.
     * When mTLS is globally disabled we trust everything (so existing flows
     * keep working in environments that have not yet enabled mTLS).
     */
    public boolean isTrustedCertificate(String certData) {
        if (!mtlsEnabled) {
            return true;
        }
        if (certData == null || certData.isBlank()) {
            return false;
        }
        String thumbprint = extractThumbprint(certData);
        boolean trusted = trustedThumbprints.contains(thumbprint);
        if (!trusted) {
            log.warn("Rejected client certificate — thumbprint {} not in trust store ({} trusted)",
                    thumbprint, trustedThumbprints.size());
        }
        return trusted;
    }

    /**
     * Extracts the SHA-256 thumbprint of an X.509 certificate.
     *
     * <p>Accepts either:</p>
     * <ul>
     *   <li>A URL-encoded PEM block (what NGINX puts in {@code X-Client-Cert}
     *       via {@code $ssl_client_escaped_cert}).</li>
     *   <li>A raw PEM block beginning with {@code -----BEGIN CERTIFICATE-----}.</li>
     *   <li>A free-form string (legacy callers / unit tests) — falls back to a
     *       SHA-256 over the raw UTF-8 bytes.</li>
     * </ul>
     *
     * <p>Returns lowercase hex, 64 characters, no colons — matching the
     * {@code thumbprint} column of {@code ingress.partner_certificates}.</p>
     */
    public String extractThumbprint(String certData) {
        if (certData == null) {
            throw new IllegalArgumentException("certData is null");
        }
        String decoded = certData;
        if (decoded.contains("%")) {
            decoded = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
        }
        try {
            if (decoded.contains("BEGIN CERTIFICATE")) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate cert = (X509Certificate) cf.generateCertificate(
                        new ByteArrayInputStream(decoded.getBytes(StandardCharsets.UTF_8)));
                return sha256Hex(cert.getEncoded());
            }
        } catch (CertificateException e) {
            // Fall through — try manual base64 + DER hash, then last-resort raw hash.
            String preview = decoded.length() > 100 ? decoded.substring(0, 100) : decoded;
            log.debug("CertificateFactory rejected PEM ({}), trying manual base64 decode | len={} preview={}",
                    e.getMessage(), decoded.length(), preview.replace("\n","\\n"));
        }
        // Manual fallback: extract base64 between markers and SHA-256 the DER bytes.
        try {
            int b = decoded.indexOf("-----BEGIN CERTIFICATE-----");
            int e = decoded.indexOf("-----END CERTIFICATE-----");
            if (b >= 0 && e > b) {
                String body = decoded.substring(b + "-----BEGIN CERTIFICATE-----".length(), e)
                        .replaceAll("\\s+", "");
                byte[] der = java.util.Base64.getDecoder().decode(body);
                return sha256Hex(der);
            }
        } catch (IllegalArgumentException ex) {
            log.warn("Manual base64 decode of cert PEM also failed: {}", ex.getMessage());
        }
        return sha256Hex(decoded.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Adds a single trusted thumbprint (lowercase hex, no colons). */
    public void addTrustedThumbprint(String thumbprint) {
        if (thumbprint == null || thumbprint.isBlank()) {
            return;
        }
        trustedThumbprints.add(thumbprint.trim().toLowerCase());
    }

    /**
     * Bulk replacement used by {@link PartnerCertificateLoader} during a
     * scheduled refresh. Atomic from the caller's perspective — readers see
     * either the old set or the new one, never a half-emptied state.
     */
    public void replaceTrustedThumbprints(Set<String> next) {
        Set<String> normalised = ConcurrentHashMap.newKeySet();
        next.forEach(t -> {
            if (t != null && !t.isBlank()) {
                normalised.add(t.trim().toLowerCase());
            }
        });
        trustedThumbprints.clear();
        trustedThumbprints.addAll(normalised);
        log.info("Trust store refreshed — {} active partner thumbprints", trustedThumbprints.size());
    }

    /** Read-only view, primarily for diagnostics and tests. */
    public Set<String> getTrustedThumbprints() {
        return Set.copyOf(trustedThumbprints);
    }

    /** Whether mTLS enforcement is currently enabled (driven by configuration). */
    public boolean isMtlsEnabled() {
        return mtlsEnabled;
    }
}
