package chit.tefca.routing.engine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Ensures at-most-once processing via idempotency keys held in an in-process
 * Caffeine cache. The single-task fat-jar deployment does not require a
 * distributed store; Caffeine with a 24h TTL is sufficient and cheaper than
 * Redis. When the gateway scales horizontally, swap for a shared store.
 */
@Component
public class IdempotencyManager {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyManager.class);
    private static final Duration TTL = Duration.ofHours(24);

    private final Cache<String, String> cache;

    public IdempotencyManager() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(100_000)
                .build();
    }

    public boolean isDuplicate(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return false;
        }
        return cache.getIfPresent("idempotency:" + idempotencyKey) != null;
    }

    public void markProcessed(String idempotencyKey, String correlationId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        cache.put("idempotency:" + idempotencyKey, correlationId);
        log.debug("Marked idempotency key={} for correlationId={}", idempotencyKey, correlationId);
    }
}
