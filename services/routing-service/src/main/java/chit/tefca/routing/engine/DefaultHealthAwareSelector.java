package chit.tefca.routing.engine;

import chit.tefca.common.model.Endpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default implementation that filters endpoints via the EndpointHealthTracker.
 * Fail-open: if all endpoints are unhealthy, returns the full list anyway.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultHealthAwareSelector implements HealthAwareSelector {

    private final EndpointHealthTracker healthTracker;

    @Override
    public List<Endpoint> selectHealthy(List<Endpoint> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<Endpoint> healthy = candidates.stream()
                .filter(ep -> healthTracker.isHealthy(ep.getEndpointId()))
                .toList();

        if (healthy.isEmpty()) {
            log.warn("All {} candidate endpoints are unhealthy — returning full list (fail-open)",
                    candidates.size());
            return candidates;
        }

        log.debug("Selected {}/{} healthy endpoints", healthy.size(), candidates.size());
        return healthy;
    }
}
