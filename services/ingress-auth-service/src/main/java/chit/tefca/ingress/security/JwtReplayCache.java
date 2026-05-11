package chit.tefca.ingress.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;

/**
 * TEFCA replay-protection store backed by an in-process Caffeine cache.
 *
 * <p>Per the TEFCA QHIN Technical Framework (and 45 CFR §164.312(c)(1)
 * integrity controls), the gateway must reject replayed authentication
 * assertions or recorded request envelopes. A single Bearer JWT may
 * legitimately be reused within its expiry, but the (jti, correlationId)
 * pair must be unique per request. An attacker re-injecting a captured
 * request collides on this key and is rejected.
 *
 * <p>The single-task fat-jar deployment uses a per-instance Caffeine cache
 * with a 15-minute window (capped to JWT expiry). When horizontal scaling
 * is required, swap this for a shared store (Redis, DynamoDB).
 *
 * <p>Disable in tests / local dev with
 * {@code tefca.security.replay-protection.enabled=false}.
 */
@Slf4j
@Component
public class JwtReplayCache {

    private static final Duration MAX_TTL = Duration.ofMinutes(15);

    private final Cache<String, Long> cache;
    private final boolean enabled;

    public JwtReplayCache(@Value("${tefca.security.replay-protection.enabled:true}") boolean enabled) {
        this.enabled = enabled;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(MAX_TTL)
                .maximumSize(500_000)
                .build();
    }

    /**
     * Record this request fingerprint. Returns true on success (first time
     * seen), false if a replay is detected.
     */
    public boolean recordOrDetectReplay(String jti, String correlationId, Instant tokenExpiry) {
        if (!enabled) {
            return true;
        }
        if (jti == null || jti.isBlank() || correlationId == null || correlationId.isBlank()) {
            log.debug("Skipping replay check (missing jti or correlationId)");
            return true;
        }

        String key = sha256(jti + "|" + correlationId);
        long expiryEpochMs = tokenExpiry != null
                ? tokenExpiry.toEpochMilli()
                : Instant.now().plus(MAX_TTL).toEpochMilli();

        Long previous = cache.asMap().putIfAbsent(key, expiryEpochMs);
        if (previous != null) {
            // Treat entries past their declared JWT expiry as not-seen.
            if (previous < System.currentTimeMillis()) {
                cache.put(key, expiryEpochMs);
                return true;
            }
            log.warn("Replay detected: jti={} correlationId={}", jti, correlationId);
            return false;
        }
        return true;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
