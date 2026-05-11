package chit.tefca.routing.engine.strategy;

import chit.tefca.common.model.Endpoint;
import chit.tefca.routing.dto.RouteRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Selects the appropriate route strategy and applies it.
 * Strategies are evaluated in @Order priority — first match wins.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteStrategySelector {

    private final List<RouteStrategy> strategies;

    /**
     * Pick the first strategy that supports the request, and apply it.
     */
    public List<Endpoint> resolve(RouteRequest request, List<Endpoint> candidates) {
        for (RouteStrategy strategy : strategies) {
            if (strategy.supports(request)) {
                log.debug("Selected strategy={} for operation={}",
                        strategy.getClass().getSimpleName(), request.getOperation());
                return strategy.resolve(request, candidates);
            }
        }
        log.warn("No strategy matched for request correlationId={}", request.getCorrelationId());
        return List.of();
    }
}
