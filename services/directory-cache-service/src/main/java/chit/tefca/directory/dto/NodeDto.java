package chit.tefca.directory.dto;

import chit.tefca.common.enums.NodeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeDto {

    private String nodeId;
    private String orgId;
    private String orgName;
    private String name;
    private String homeCommunityId;
    private NodeStatus status;
    private int endpointCount;
    private List<CapabilityDto> capabilities;
    private Instant createdAt;
    private Instant updatedAt;
}
