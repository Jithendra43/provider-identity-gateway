package chit.tefca.directory.service;

import chit.tefca.common.enums.NodeStatus;
import chit.tefca.common.exception.DirectoryLookupException;
import chit.tefca.directory.cache.DirectoryCaffeineCache;
import chit.tefca.directory.dto.CapabilityDto;
import chit.tefca.directory.dto.NodeDto;
import chit.tefca.directory.model.DirectoryCapability;
import chit.tefca.directory.model.DirectoryNode;
import chit.tefca.directory.repository.CapabilityRepository;
import chit.tefca.directory.repository.EndpointRepository;
import chit.tefca.directory.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NodeService {

    private static final Logger log = LoggerFactory.getLogger(NodeService.class);

    private final NodeRepository nodeRepository;
    private final EndpointRepository endpointRepository;
    private final CapabilityRepository capabilityRepository;
    private final DirectoryCaffeineCache caffeineCache;

    public NodeDto getNode(String nodeId) {
        Optional<NodeDto> cached = caffeineCache.getCachedNode(nodeId);
        if (cached.isPresent()) {
            return cached.get();
        }

        DirectoryNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new DirectoryLookupException("Node not found: " + nodeId));

        NodeDto dto = toDto(node);
        caffeineCache.cacheNode(nodeId, dto);
        return dto;
    }

    public List<NodeDto> getNodesByOrgId(String orgId) {
        return nodeRepository.findActiveByOrgId(orgId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<NodeDto> getNodesByStatus(NodeStatus status) {
        return nodeRepository.findByStatus(status).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private NodeDto toDto(DirectoryNode node) {
        List<CapabilityDto> caps = capabilityRepository
                .findByNodeIdAndEnabledTrue(node.getNodeId()).stream()
                .map(this::toCapDto)
                .collect(Collectors.toList());

        long epCount = endpointRepository.countByNodeId(node.getNodeId());

        return NodeDto.builder()
                .nodeId(node.getNodeId())
                .orgId(node.getOrgId())
                .name(node.getName())
                .homeCommunityId(node.getHomeCommunityId())
                .status(node.getStatus())
                .endpointCount((int) epCount)
                .capabilities(caps)
                .createdAt(node.getCreatedAt())
                .updatedAt(node.getUpdatedAt())
                .build();
    }

    private CapabilityDto toCapDto(DirectoryCapability cap) {
        return CapabilityDto.builder()
                .capabilityId(cap.getCapabilityId())
                .nodeId(cap.getNodeId())
                .modality(cap.getModality())
                .operation(cap.getOperation())
                .enabled(cap.isEnabled())
                .build();
    }
}
