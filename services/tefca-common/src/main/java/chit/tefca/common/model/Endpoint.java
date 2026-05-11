package chit.tefca.common.model;

import chit.tefca.common.enums.Modality;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a network endpoint for a node.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Endpoint {
    private String endpointId;
    private String nodeId;
    private String url;
    private Modality modality;
    private boolean active;
    private String certificateAlias;

    /** Optional per-endpoint forward timeout. NULL falls back to the modality default in TransactionForwarder. */
    private Integer timeoutMs;

    /** Optional URL the EndpointHealthTracker can probe for liveness. */
    private String healthCheckUrl;
}
