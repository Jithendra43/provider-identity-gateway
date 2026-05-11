package chit.tefca.directory.dto;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityDto {

    private String capabilityId;
    private String nodeId;
    private Modality modality;
    private TefcaOperation operation;
    private boolean enabled;
}
