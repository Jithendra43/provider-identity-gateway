package chit.tefca.common.spi;

import chit.tefca.common.model.Endpoint;

import java.util.List;

/**
 * In-process SPI for resolving directory endpoints without the HTTP hop.
 *
 * <p>When the gateway runs as the consolidated fat-jar
 * ({@code tefca-gateway-app}), the directory-cache-service module provides a
 * Spring bean implementing this interface. The routing-service detects it via
 * optional injection and uses it instead of the WebClient-backed
 * {@code DirectoryCacheClient}, eliminating the loopback TLS handshake and
 * JSON serialization round-trip. This keeps the PA hot path inside the
 * 500&nbsp;ms partner timeout with consistent single-digit-millisecond
 * directory lookups.</p>
 *
 * <p>If the bean is not present (e.g. when routing-service is deployed
 * standalone in a multi-container topology), callers fall back to HTTP.</p>
 */
public interface LocalEndpointResolver {

    /** All active endpoints for the target organization. */
    List<Endpoint> getEndpointsByOrgId(String orgId);

    /** All active endpoints for the target node. */
    List<Endpoint> getEndpointsByNodeId(String nodeId);
}
