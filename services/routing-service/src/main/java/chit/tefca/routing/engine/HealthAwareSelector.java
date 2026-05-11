package chit.tefca.routing.engine;

import chit.tefca.common.model.Endpoint;

import java.util.List;

/**
 * Filters a candidate endpoint list to only include endpoints
 * that are considered healthy by the health tracker.
 */
public interface HealthAwareSelector {

    /**
     * Select healthy endpoints from the candidates.
     * If all are unhealthy, returns the full list (fail-open).
     */
    List<Endpoint> selectHealthy(List<Endpoint> candidates);
}
