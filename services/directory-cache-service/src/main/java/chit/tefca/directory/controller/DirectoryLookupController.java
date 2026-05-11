package chit.tefca.directory.controller;

import chit.tefca.common.enums.NodeStatus;
import chit.tefca.directory.dto.EndpointLookupRequest;
import chit.tefca.directory.dto.EndpointLookupResponse;
import chit.tefca.directory.dto.NodeDto;
import chit.tefca.directory.service.EndpointLookupService;
import chit.tefca.directory.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/directory")
@RequiredArgsConstructor
public class DirectoryLookupController {

    private final EndpointLookupService endpointLookupService;
    private final NodeService nodeService;

    @PostMapping("/endpoints/lookup")
    public ResponseEntity<List<EndpointLookupResponse>> lookupEndpoints(
            @RequestBody EndpointLookupRequest request) {
        return ResponseEntity.ok(endpointLookupService.lookupEndpoints(request));
    }

    @GetMapping("/organizations/{orgId}/endpoints")
    public ResponseEntity<List<EndpointLookupResponse>> getOrgEndpoints(@PathVariable String orgId) {
        return ResponseEntity.ok(endpointLookupService.getEndpointsForOrg(orgId));
    }

    @GetMapping("/nodes/{nodeId}/endpoints")
    public ResponseEntity<List<EndpointLookupResponse>> getNodeEndpoints(@PathVariable String nodeId) {
        return ResponseEntity.ok(endpointLookupService.getEndpointsForNode(nodeId));
    }

    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<NodeDto> getNode(@PathVariable String nodeId) {
        return ResponseEntity.ok(nodeService.getNode(nodeId));
    }

    @GetMapping("/nodes")
    public ResponseEntity<List<NodeDto>> getNodes(
            @RequestParam(required = false) String orgId,
            @RequestParam(required = false) NodeStatus status) {
        if (orgId != null) {
            return ResponseEntity.ok(nodeService.getNodesByOrgId(orgId));
        }
        if (status != null) {
            return ResponseEntity.ok(nodeService.getNodesByStatus(status));
        }
        return ResponseEntity.ok(nodeService.getNodesByStatus(NodeStatus.ACTIVE));
    }
}
