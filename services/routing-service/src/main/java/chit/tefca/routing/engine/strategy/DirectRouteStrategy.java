package chit.tefca.routing.engine.strategy;

import chit.tefca.common.model.Endpoint;
import chit.tefca.routing.dto.RouteRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Direct routing strategy: picks the first endpoint matching the requested modality.
 * This is the default strategy for most TEFCA operations.
 */
@Slf4j
@Component
@Order(1)
public class DirectRouteStrategy implements RouteStrategy {

    @Override
    public List<Endpoint> resolve(RouteRequest request, List<Endpoint> candidates) {
        return candidates.stream()
                .filter(ep -> ep.getModality() == request.getModality())
                .filter(Endpoint::isActive)
                .limit(1)
                .toList();
    }

    @Override
    public boolean supports(RouteRequest request) {
        // Default strategy — supports all requests
        return true;
    }
}
