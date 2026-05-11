package chit.tefca.routing.engine.strategy;

import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.common.model.Endpoint;
import chit.tefca.routing.dto.RouteRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Fan-out strategy: returns ALL matching endpoints for broadcast-style operations
 * like patient discovery (XCPD), where the query is sent to multiple responders.
 */
@Slf4j
@Component
@Order(0)
public class FanOutRouteStrategy implements RouteStrategy {

    private static final Set<TefcaOperation> FAN_OUT_OPERATIONS = Set.of(
            TefcaOperation.PATIENT_DISCOVERY
    );

    @Override
    public List<Endpoint> resolve(RouteRequest request, List<Endpoint> candidates) {
        List<Endpoint> matching = candidates.stream()
                .filter(ep -> ep.getModality() == request.getModality())
                .filter(Endpoint::isActive)
                .toList();
        log.debug("Fan-out returning {} endpoints for operation={}", matching.size(), request.getOperation());
        return matching;
    }

    @Override
    public boolean supports(RouteRequest request) {
        return FAN_OUT_OPERATIONS.contains(request.getOperation());
    }
}
