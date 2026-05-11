package chit.tefca.directory.spi;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.model.Endpoint;
import chit.tefca.common.spi.LocalEndpointResolver;
import chit.tefca.directory.dto.EndpointLookupRequest;
import chit.tefca.directory.dto.EndpointLookupResponse;
import chit.tefca.directory.service.EndpointLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bean implementation of {@link LocalEndpointResolver} that delegates to the
 * in-process {@link EndpointLookupService}. Loaded automatically whenever the
 * directory-cache-service module is on the classpath (true for the
 * consolidated {@code tefca-gateway-app} fat-jar deployment).
 */
@Component
@RequiredArgsConstructor
public class LocalEndpointResolverImpl implements LocalEndpointResolver {

    private final EndpointLookupService endpointLookupService;

    @Override
    public List<Endpoint> getEndpointsByOrgId(String orgId) {
        EndpointLookupRequest req = EndpointLookupRequest.builder().targetOrgId(orgId).build();
        return endpointLookupService.lookupEndpoints(req).stream().map(this::toEndpoint).toList();
    }

    @Override
    public List<Endpoint> getEndpointsByNodeId(String nodeId) {
        EndpointLookupRequest req = EndpointLookupRequest.builder().targetNodeId(nodeId).build();
        return endpointLookupService.lookupEndpoints(req).stream().map(this::toEndpoint).toList();
    }

    private Endpoint toEndpoint(EndpointLookupResponse r) {
        Modality m = r.getModality();
        return Endpoint.builder()
                .endpointId(r.getEndpointId())
                .nodeId(r.getNodeId())
                .url(r.getUrl())
                .modality(m)
                .active(r.isActive())
                .certificateAlias(r.getCertificateAlias())
                .timeoutMs(r.getTimeoutMs())
                .healthCheckUrl(r.getHealthCheckUrl())
                .build();
    }
}
