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
public class EndpointLookupRequest {

    private String targetOrgId;
    private String targetNodeId;
    private Modality modality;
}
