package chit.tefca.routing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tracks the health status of a remote endpoint.
 * Stored in Redis as a JSON value with automatic TTL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointHealth {

    private String endpointId;
    private String endpointUrl;
    private boolean healthy;
    private int consecutiveFailures;
    private long avgResponseTimeMs;
    private Instant lastChecked;
    private Instant lastFailure;
    private String lastError;
}
