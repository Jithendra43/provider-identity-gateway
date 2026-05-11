package chit.tefca.routing.model;

import chit.tefca.common.enums.Modality;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Record of a completed route execution — stored in Redis for observability.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteExecution {

    private String correlationId;
    private String requesterOrgId;
    private String targetOrgId;
    private Modality modality;
    private String resolvedEndpointId;
    private String resolvedEndpointUrl;
    private int httpStatus;
    private long routingDurationMs;
    private long forwardDurationMs;
    private boolean success;
    private String errorMessage;
    private Instant completedAt;
}
