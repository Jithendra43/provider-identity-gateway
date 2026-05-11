package chit.tefca.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Generates deterministic idempotency keys from request components.
 */
public final class IdempotencyKeyGenerator {

    private IdempotencyKeyGenerator() {
    }

    public static String generate(String correlationId, String operation, String requesterOrgId) {
        String input = correlationId + "|" + operation + "|" + requesterOrgId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
