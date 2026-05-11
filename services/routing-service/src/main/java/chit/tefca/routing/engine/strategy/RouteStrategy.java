package chit.tefca.routing.engine.strategy;

import chit.tefca.common.model.Endpoint;
import chit.tefca.routing.dto.RouteRequest;

import java.util.List;

/**
 * Strategy interface for resolving one or more endpoints for a route request.
 */
public interface RouteStrategy {

    /**
     * Resolve the target endpoint(s) for the given route request.
     */
    List<Endpoint> resolve(RouteRequest request, List<Endpoint> candidates);

    /**
     * Whether this strategy supports the given request.
     */
    boolean supports(RouteRequest request);
}
