package chit.tefca.ingress.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * One-way salted hash of patient identifiers for audit storage. Implements the
 * spirit of 45 CFR §164.514(b) (Safe Harbor de-identification) for the audit
 * channel: an observer with audit-log access cannot recover the original MRN.
 *
 * Salt is sourced from the {@code TEFCA_AUDIT_PATIENT_SALT} environment
 * variable. If not configured, falls back to a build-time constant — operators
 * MUST override this in production to avoid rainbow-table attacks.
 */
public final class PatientIdHasher {

    private static final String SALT;
    private static final String FALLBACK_SALT = "tefca-gw-default-salt-DO-NOT-USE-IN-PROD";

    static {
        String env = System.getenv("TEFCA_AUDIT_PATIENT_SALT");
        SALT = (env == null || env.isBlank()) ? FALLBACK_SALT : env;
    }

    private PatientIdHasher() {}

    public static String hash(String patientId) {
        if (patientId == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(SALT.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            byte[] out = md.digest(patientId.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : out) sb.append(String.format("%02x", b));
            // Truncate to 32 hex chars (128 bits) for log compactness; still
            // collision-resistant for audit attribution scope.
            return "sha256:" + sb.substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by JDK spec; this is unreachable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
