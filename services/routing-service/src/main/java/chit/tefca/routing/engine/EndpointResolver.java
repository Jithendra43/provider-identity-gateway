package chit.tefca.routing.engine;

import chit.tefca.common.model.Endpoint;
import chit.tefca.routing.client.DirectoryCacheClient;
import chit.tefca.routing.dto.RouteRequest;
import chit.tefca.routing.engine.strategy.RouteStrategySelector;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves the best endpoint for a given route request
 * by querying the directory-cache-service, applying health filtering,
 * and selecting via route strategy.
 */
@Component
@RequiredArgsConstructor
public class EndpointResolver {

    private static final Logger log = LoggerFactory.getLogger(EndpointResolver.class);

    private final DirectoryCacheClient directoryCacheClient;
    private final HealthAwareSelector healthAwareSelector;
    private final RouteStrategySelector strategySelector;

    /**
     * Resolves a single endpoint for the request.
     * For fan-out operations, returns the first resolved endpoint;
     * use {@link #resolveAll(RouteRequest)} for multi-endpoint resolution.
     */
    public Endpoint resolve(RouteRequest request) {
        List<Endpoint> resolved = resolveAll(request);
        if (resolved.isEmpty()) {
            throw new IllegalStateException(
                    "No endpoint found for targetOrg=" + request.getTargetOrgId()
                    + " modality=" + request.getModality());
        }
        return resolved.get(0);
    }

    /**
     * Resolve all matching endpoints (health-filtered + strategy-selected).
     */
    public List<Endpoint> resolveAll(RouteRequest request) {
        log.info("Resolving endpoint for targetOrg={} modality={}",
                request.getTargetOrgId(), request.getModality());

        // Fetch candidates from directory cache
        List<Endpoint> candidates;
        if (request.getTargetNodeId() != null) {
            candidates = directoryCacheClient.getEndpointsByNodeId(request.getTargetNodeId());
        } else {
            candidates = directoryCacheClient.getEndpointsByOrgId(request.getTargetOrgId());
        }

        if (candidates == null || candidates.isEmpty()) {
            log.warn("No endpoints found in directory for targetOrg={}", request.getTargetOrgId());
            return List.of();
        }

        // Filter by modality. The directory join returns every endpoint
        // owned by the target organisation. Without this filter the PA
        // workflow (one org owning 14 endpoints across CRD/DTR/PAS) would
        // fan out to the wrong service. Modality is the cheapest, most
        // deterministic discriminator we have.
        if (request.getModality() != null) {
            candidates = candidates.stream()
                    .filter(e -> e.getModality() == request.getModality())
                    .toList();
            if (candidates.isEmpty()) {
                log.warn("No endpoints matched modality={} for targetOrg={}",
                        request.getModality(), request.getTargetOrgId());
                return List.of();
            }
        }

        // Filter by health
        List<Endpoint> healthy = healthAwareSelector.selectHealthy(candidates);

        // Apply routing strategy
        List<Endpoint> selected = strategySelector.resolve(request, healthy);
        log.debug("Resolved {} endpoint(s) for correlationId={}", selected.size(), request.getCorrelationId());
        return selected;
    }
}
