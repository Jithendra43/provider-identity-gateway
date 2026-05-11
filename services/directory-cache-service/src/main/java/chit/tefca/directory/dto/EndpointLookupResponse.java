package chit.tefca.directory.dto;

import chit.tefca.common.enums.Modality;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointLookupResponse {

    private String endpointId;
    private String nodeId;
    private String orgId;
    private String url;
    private Modality modality;
    private boolean active;
    private String certificateAlias;
    private Integer timeoutMs;
    private String healthCheckUrl;
}
