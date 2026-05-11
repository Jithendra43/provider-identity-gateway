package chit.tefca.routing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {

    private String correlationId;
    private String resolvedEndpointUrl;
    private String resolvedNodeId;
    private int httpStatus;
    private Map<String, Object> responsePayload;
    private long routingDurationMs;
    private long forwardDurationMs;
    private Instant completedAt;
}
