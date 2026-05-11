package chit.tefca.routing.engine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import chit.tefca.routing.model.EndpointHealth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Manages endpoint health tracking in an in-process Caffeine cache.
 * Records successes/failures and provides health lookups for routing decisions.
 * Per-instance state is acceptable in the single-task fat-jar deployment.
 */
@Slf4j
@Component
public class EndpointHealthTracker {

    private static final Duration TTL = Duration.ofHours(1);
    private static final int UNHEALTHY_THRESHOLD = 3;

    private final Cache<String, EndpointHealth> cache;

    public EndpointHealthTracker() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(10_000)
                .build();
    }

    public void recordSuccess(String endpointId, String endpointUrl, long responseTimeMs) {
        EndpointHealth health = getHealth(endpointId).orElse(
                EndpointHealth.builder()
                        .endpointId(endpointId)
                        .endpointUrl(endpointUrl)
                        .build());

        health.setHealthy(true);
        health.setConsecutiveFailures(0);
        health.setAvgResponseTimeMs((health.getAvgResponseTimeMs() + responseTimeMs) / 2);
        health.setLastChecked(Instant.now());
        cache.put(endpointId, health);
    }

    public void recordFailure(String endpointId, String endpointUrl, String error) {
        EndpointHealth health = getHealth(endpointId).orElse(
                EndpointHealth.builder()
                        .endpointId(endpointId)
                        .endpointUrl(endpointUrl)
                        .build());

        int failures = health.getConsecutiveFailures() + 1;
        health.setConsecutiveFailures(failures);
        health.setHealthy(failures < UNHEALTHY_THRESHOLD);
        health.setLastFailure(Instant.now());
        health.setLastChecked(Instant.now());
        health.setLastError(error);
        cache.put(endpointId, health);
    }

    public boolean isHealthy(String endpointId) {
        return getHealth(endpointId).map(EndpointHealth::isHealthy).orElse(true);
    }

    public Optional<EndpointHealth> getHealth(String endpointId) {
        return Optional.ofNullable(cache.getIfPresent(endpointId));
    }
}
