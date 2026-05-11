package chit.tefca.routing.engine.strategy;

import chit.tefca.common.model.Endpoint;
import chit.tefca.routing.dto.RouteRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback routing strategy: when no matching active endpoint is found for the
 * requested modality, this strategy selects ANY active endpoint as a last resort.
 * Has the lowest priority (highest order number) — only used when no other strategy matches.
 */
@Slf4j
@Component
@Order(100)
public class FallbackRouteStrategy implements RouteStrategy {

    @Override
    public List<Endpoint> resolve(RouteRequest request, List<Endpoint> candidates) {
        List<Endpoint> fallback = candidates.stream()
                .filter(Endpoint::isActive)
                .limit(1)
                .toList();
        if (fallback.isEmpty()) {
            log.warn("Fallback strategy found no active endpoints for correlationId={}",
                    request.getCorrelationId());
        } else {
            log.info("Fallback strategy selected endpoint={} for correlationId={} (modality mismatch tolerated)",
                    fallback.getFirst().getEndpointId(), request.getCorrelationId());
        }
        return fallback;
    }

    @Override
    public boolean supports(RouteRequest request) {
        // Fallback always supports — but @Order(100) ensures it runs last
        return true;
    }
}
