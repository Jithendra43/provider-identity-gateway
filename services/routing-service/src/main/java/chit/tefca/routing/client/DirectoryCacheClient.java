package chit.tefca.routing.client;

import chit.tefca.common.model.Endpoint;
import chit.tefca.common.spi.LocalEndpointResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * Resolves directory endpoints. Prefers the in-process
 * {@link LocalEndpointResolver} bean (present whenever directory-cache-service
 * runs in the same JVM, which is the case for the consolidated
 * {@code tefca-gateway-app} fat-jar) to avoid an HTTP loopback round-trip in
 * the PA hot path. Falls back to a WebClient call when the bean is absent
 * (multi-container topology).
 */
@Slf4j
@Component("routingDirectoryCacheClient")
public class DirectoryCacheClient {

    private final WebClient webClient;
    private final Duration timeout;
    private final ObjectProvider<LocalEndpointResolver> localResolverProvider;

    public DirectoryCacheClient(WebClient.Builder webClientBuilder,
                                @Value("${tefca.services.directory-cache-url}") String baseUrl,
                                @Value("${tefca.timeouts.endpoint-resolution-ms:2000}") long timeoutMs,
                                ObjectProvider<LocalEndpointResolver> localResolverProvider) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.timeout = Duration.ofMillis(timeoutMs);
        this.localResolverProvider = localResolverProvider;
    }

    /**
     * Look up all active endpoints for an organization.
     */
    public List<Endpoint> getEndpointsByOrgId(String orgId) {
        log.debug("Looking up endpoints for orgId={}", orgId);
        LocalEndpointResolver local = localResolverProvider.getIfAvailable();
        if (local != null) {
            return local.getEndpointsByOrgId(orgId);
        }
        return webClient.get()
                .uri("/api/v1/directory/organizations/{orgId}/endpoints", orgId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Endpoint>>() {})
                .timeout(timeout)
                .block();
    }

    /**
     * Look up endpoints for an organization filtered by node.
     */
    public List<Endpoint> getEndpointsByNodeId(String nodeId) {
        log.debug("Looking up endpoints for nodeId={}", nodeId);
        LocalEndpointResolver local = localResolverProvider.getIfAvailable();
        if (local != null) {
            return local.getEndpointsByNodeId(nodeId);
        }
        return webClient.get()
                .uri("/api/v1/directory/nodes/{nodeId}/endpoints", nodeId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Endpoint>>() {})
                .timeout(timeout)
                .block();
    }
}

