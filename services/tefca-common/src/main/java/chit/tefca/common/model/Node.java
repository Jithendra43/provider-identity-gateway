package chit.tefca.common.model;

import chit.tefca.common.enums.NodeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a node (endpoint system) within an organization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Node {
    private String nodeId;
    private String orgId;
    private String name;
    private String homeCommunityId;
    private NodeStatus status;
}
