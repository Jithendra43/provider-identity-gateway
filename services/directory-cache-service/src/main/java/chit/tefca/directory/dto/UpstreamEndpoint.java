package chit.tefca.directory.dto;

import chit.tefca.common.enums.Modality;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Endpoint record as returned by the upstream RCE/QHIN directory.
 * Decoupled from the JPA entity so we can evolve persistence independently
 * of the upstream wire format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpstreamEndpoint {

    private String endpointId;
    private String nodeId;
    private String url;
    private Modality modality;
    private boolean active;
    private String certificateAlias;
    private String supportedOperations;
}
