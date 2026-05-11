package chit.tefca.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signing for internal service-to-service request authentication.
 * Each request is signed with: HMAC(secret, timestamp + ":" + path + ":" + bodyHash)
 */
public final class HmacRequestSigner {

    public static final String HEADER_SIGNATURE = "X-TEFCA-Signature";
    public static final String HEADER_TIMESTAMP = "X-TEFCA-Timestamp";
    private static final String ALGORITHM = "HmacSHA256";

    private HmacRequestSigner() {
    }

    /**
     * Generates an HMAC-SHA256 signature for the given request components.
     */
    public static String sign(String secret, String timestamp, String path, String body) {
        String payload = timestamp + ":" + path + ":" + hash(body);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signature);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    /**
     * Verifies that the provided signature matches the expected one.
     * Tolerates clock skew up to the specified max seconds.
     */
    public static boolean verify(String secret, String signature, String timestamp,
                                  String path, String body, long maxSkewSeconds) {
        long ts = Long.parseLong(timestamp);
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > maxSkewSeconds) {
            return false;
        }
        String expected = sign(secret, timestamp, path, body);
        return constant_time_equals(expected, signature);
    }

    private static String hash(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input != null ? input.getBytes(StandardCharsets.UTF_8) : new byte[0]);
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static boolean constant_time_equals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
