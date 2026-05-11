package chit.tefca.directory.service;

import chit.tefca.common.exception.DirectoryLookupException;
import chit.tefca.directory.cache.DirectoryCaffeineCache;
import chit.tefca.directory.dto.EndpointLookupRequest;
import chit.tefca.directory.dto.EndpointLookupResponse;
import chit.tefca.directory.model.DirectoryEndpoint;
import chit.tefca.directory.model.DirectoryNode;
import chit.tefca.directory.repository.EndpointRepository;
import chit.tefca.directory.repository.NodeRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EndpointLookupService {

    private static final Logger log = LoggerFactory.getLogger(EndpointLookupService.class);

    private final NodeRepository nodeRepository;
    private final EndpointRepository endpointRepository;
    private final DirectoryCaffeineCache caffeineCache;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public EndpointLookupService(NodeRepository nodeRepository,
                                  EndpointRepository endpointRepository,
                                  DirectoryCaffeineCache caffeineCache,
                                  MeterRegistry meterRegistry) {
        this.nodeRepository = nodeRepository;
        this.endpointRepository = endpointRepository;
        this.caffeineCache = caffeineCache;
        this.cacheHitCounter = Counter.builder("directory.cache.hit")
                .description("Directory cache hits")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("directory.cache.miss")
                .description("Directory cache misses")
                .register(meterRegistry);
    }

    public List<EndpointLookupResponse> lookupEndpoints(EndpointLookupRequest request) {
        validateRequest(request);

        String orgId = request.getTargetOrgId();
        String modality = request.getModality() != null ? request.getModality().name() : null;

        log.debug("Endpoint lookup: orgId={}, nodeId={}, modality={}",
                orgId, request.getTargetNodeId(), modality);

        // Try Redis cache first
        Optional<List<EndpointLookupResponse>> cached =
                caffeineCache.getCachedEndpoints(orgId, modality);
        if (cached.isPresent()) {
            cacheHitCounter.increment();
            return cached.get();
        }
        cacheMissCounter.increment();

        // Fall back to PostgreSQL
        List<EndpointLookupResponse> results = lookupFromDatabase(request);

        // Populate cache for subsequent calls
        if (!results.isEmpty()) {
            caffeineCache.cacheEndpoints(orgId, modality, results);
        }

        return results;
    }

    public List<EndpointLookupResponse> getEndpointsForOrg(String orgId) {
        return lookupEndpoints(EndpointLookupRequest.builder().targetOrgId(orgId).build());
    }

    public List<EndpointLookupResponse> getEndpointsForNode(String nodeId) {
        return lookupEndpoints(EndpointLookupRequest.builder().targetNodeId(nodeId).build());
    }

    private List<EndpointLookupResponse> lookupFromDatabase(EndpointLookupRequest request) {
        // If specific node requested, look up directly
        if (request.getTargetNodeId() != null) {
            return lookupByNode(request);
        }

        // Otherwise look up by org → all active nodes → their endpoints
        if (request.getModality() != null) {
            return endpointRepository
                    .findActiveByOrgIdAndModality(request.getTargetOrgId(), request.getModality())
                    .stream()
                    .map(ep -> toResponse(ep, request.getTargetOrgId()))
                    .collect(Collectors.toList());
        }

        return endpointRepository.findActiveByOrgId(request.getTargetOrgId())
                .stream()
                .map(ep -> toResponse(ep, request.getTargetOrgId()))
                .collect(Collectors.toList());
    }

    private List<EndpointLookupResponse> lookupByNode(EndpointLookupRequest request) {
        DirectoryNode node = nodeRepository.findById(request.getTargetNodeId())
                .orElseThrow(() -> new DirectoryLookupException(
                        "Node not found: " + request.getTargetNodeId()));

        List<DirectoryEndpoint> endpoints;
        if (request.getModality() != null) {
            endpoints = endpointRepository.findByNodeIdAndModalityAndActiveTrue(
                    node.getNodeId(), request.getModality());
        } else {
            endpoints = endpointRepository.findByNodeIdAndActiveTrue(node.getNodeId());
        }

        return endpoints.stream()
                .map(ep -> toResponse(ep, node.getOrgId()))
                .collect(Collectors.toList());
    }

    private void validateRequest(EndpointLookupRequest request) {
        if (request.getTargetOrgId() == null && request.getTargetNodeId() == null) {
            throw new DirectoryLookupException("Either targetOrgId or targetNodeId must be provided");
        }
    }

    private EndpointLookupResponse toResponse(DirectoryEndpoint ep, String orgId) {
        return EndpointLookupResponse.builder()
                .endpointId(ep.getEndpointId())
                .nodeId(ep.getNodeId())
                .orgId(orgId)
                .url(ep.getUrl())
                .modality(ep.getModality())
                .active(ep.isActive())
                .certificateAlias(ep.getCertificateAlias())
                .timeoutMs(ep.getTimeoutMs())
                .healthCheckUrl(ep.getHealthCheckUrl())
                .build();
    }
}
