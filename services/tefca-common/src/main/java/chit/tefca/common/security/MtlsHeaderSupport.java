package chit.tefca.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Extracts the client certificate PEM from whichever header the upstream
 * TLS terminator put it in. Supports both the NGINX-style {@code X-Client-Cert}
 * (used by the local docker-compose harness) and the AWS ALB
 * {@code X-Amzn-Mtls-Clientcert*} (URL-encoded PEM) used in production.
 */
public final class MtlsHeaderSupport {

    private static final Logger log = LoggerFactory.getLogger(MtlsHeaderSupport.class);
    private static final String PEM_BEGIN = "-----BEGIN CERTIFICATE-----";
    private static final String PEM_END = "-----END CERTIFICATE-----";

    private MtlsHeaderSupport() {
    }

    /**
     * @return the client cert PEM, or {@code null} if neither header is present.
     */
    public static String extractClientCertPem(HttpServletRequest request) {
        String pem = request.getHeader(SecurityConstants.MTLS_CLIENT_CERT_HEADER);
        if (pem != null && !pem.isBlank()) {
            return normalize(pem);
        }
        String leaf = request.getHeader(SecurityConstants.MTLS_CLIENT_CERT_HEADER_ALB_LEAF);
        if (leaf != null && !leaf.isBlank()) {
            return normalize(decode(leaf));
        }
        String alb = request.getHeader(SecurityConstants.MTLS_CLIENT_CERT_HEADER_ALB);
        if (alb != null && !alb.isBlank()) {
            return normalize(decode(alb));
        }
        return null;
    }

    private static String decode(String value) {
        // RFC 3986 percent-decoding only — DO NOT convert '+' to space.
        // ALB sends base64 PEM verbatim (which contains '+' and '/' chars);
        // java.net.URLDecoder applies form-decoding which would corrupt the
        // base64 by replacing every '+' with a space.
        try {
            int len = value.length();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(len);
            int i = 0;
            while (i < len) {
                char c = value.charAt(i);
                if (c == '%' && i + 2 < len) {
                    int hi = Character.digit(value.charAt(i + 1), 16);
                    int lo = Character.digit(value.charAt(i + 2), 16);
                    if (hi >= 0 && lo >= 0) {
                        out.write((hi << 4) | lo);
                        i += 3;
                        continue;
                    }
                }
                // Default: write the UTF-8 bytes of the literal char.
                out.write(String.valueOf(c).getBytes(StandardCharsets.UTF_8), 0,
                        String.valueOf(c).getBytes(StandardCharsets.UTF_8).length);
                i++;
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            log.warn("Percent-decode of mTLS header failed: {}", e.getMessage());
            return value;
        }
    }

    /**
     * Some terminators strip newlines or send a single-line PEM. Java's
     * {@code CertificateFactory} requires line breaks. Reformat to a canonical
     * 64-char-per-line PEM block when needed.
     */
    private static String normalize(String pem) {
        if (pem == null) return null;
        String trimmed = pem.trim();
        if (!trimmed.contains(PEM_BEGIN)) {
            return trimmed;
        }
        // Already multi-line PEM → return as-is.
        if (trimmed.indexOf('\n') > 0) {
            return trimmed;
        }
        int b = trimmed.indexOf(PEM_BEGIN) + PEM_BEGIN.length();
        int e = trimmed.indexOf(PEM_END);
        if (e <= b) return trimmed;
        String body = trimmed.substring(b, e).replaceAll("\\s+", "");
        StringBuilder sb = new StringBuilder(body.length() + 100);
        sb.append(PEM_BEGIN).append('\n');
        for (int i = 0; i < body.length(); i += 64) {
            sb.append(body, i, Math.min(i + 64, body.length())).append('\n');
        }
        sb.append(PEM_END).append('\n');
        return sb.toString();
    }
}
