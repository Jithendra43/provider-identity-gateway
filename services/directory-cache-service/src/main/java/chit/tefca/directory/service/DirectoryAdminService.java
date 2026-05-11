package chit.tefca.directory.service;

import chit.tefca.common.exception.DirectoryLookupException;
import chit.tefca.directory.cache.DirectoryCacheManager;
import chit.tefca.directory.dto.OrganizationDto;
import chit.tefca.directory.model.DirectoryEndpoint;
import chit.tefca.directory.model.DirectoryNode;
import chit.tefca.directory.model.DirectoryOrganization;
import chit.tefca.directory.repository.EndpointRepository;
import chit.tefca.directory.repository.NodeRepository;
import chit.tefca.directory.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectoryAdminService {

    private static final Logger log = LoggerFactory.getLogger(DirectoryAdminService.class);

    private final OrganizationRepository organizationRepository;
    private final NodeRepository nodeRepository;
    private final EndpointRepository endpointRepository;
    private final DirectoryCacheManager cacheManager;

    @Transactional
    public OrganizationDto createOrganization(DirectoryOrganization org) {
        if (organizationRepository.existsById(org.getOrgId())) {
            throw new DirectoryLookupException("Organization already exists: " + org.getOrgId());
        }
        DirectoryOrganization saved = organizationRepository.save(org);
        log.info("Created organization: {}", saved.getOrgId());
        return toDto(saved);
    }

    @Transactional
    public OrganizationDto updateOrganization(String orgId, DirectoryOrganization updates) {
        DirectoryOrganization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new DirectoryLookupException("Organization not found: " + orgId));

        org.setName(updates.getName());
        org.setOid(updates.getOid());
        org.setOrgType(updates.getOrgType());
        org.setActive(updates.isActive());
        org.setHomeCommunityId(updates.getHomeCommunityId());

        DirectoryOrganization saved = organizationRepository.save(org);
        cacheManager.invalidateOrganization(orgId);
        log.info("Updated organization: {}", orgId);
        return toDto(saved);
    }

    @Transactional
    public void deactivateOrganization(String orgId) {
        DirectoryOrganization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new DirectoryLookupException("Organization not found: " + orgId));
        org.setActive(false);
        organizationRepository.save(org);
        cacheManager.invalidateOrganization(orgId);
        log.info("Deactivated organization: {}", orgId);
    }

    @Transactional
    public void createNode(DirectoryNode node) {
        if (!organizationRepository.existsById(node.getOrgId())) {
            throw new DirectoryLookupException("Parent organization not found: " + node.getOrgId());
        }
        nodeRepository.save(node);
        cacheManager.invalidateOrganization(node.getOrgId());
        log.info("Created node: {} for org: {}", node.getNodeId(), node.getOrgId());
    }

    @Transactional
    public void createEndpoint(DirectoryEndpoint endpoint) {
        DirectoryNode node = nodeRepository.findById(endpoint.getNodeId())
                .orElseThrow(() -> new DirectoryLookupException("Node not found: " + endpoint.getNodeId()));
        endpointRepository.save(endpoint);
        cacheManager.invalidateOrganization(node.getOrgId());
        log.info("Created endpoint: {} for node: {}", endpoint.getEndpointId(), endpoint.getNodeId());
    }

    /**
     * Patch an existing endpoint's URL and/or active flag. Used by the admin
     * Directory page so operators can re-point a registered endpoint at a
     * different downstream host (e.g. swap mock-pa for a real partner URL)
     * without applying a SQL migration. Only mutable fields are touched;
     * endpointId, nodeId, and modality are immutable identity attributes.
     */
    /**
     * Delete an endpoint by id. Invalidates the per-org cache so the next
     * routing lookup does not return a stale URL.
     */
    @Transactional
    public void deleteEndpoint(String endpointId) {
        DirectoryEndpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new DirectoryLookupException("Endpoint not found: " + endpointId));
        String nodeId = endpoint.getNodeId();
        endpointRepository.delete(endpoint);
        nodeRepository.findById(nodeId).ifPresent(n -> cacheManager.invalidateOrganization(n.getOrgId()));
        log.info("Deleted endpoint {} (node={})", endpointId, nodeId);
    }

    /**
     * Delete a node by id. Refuses (409 at the controller boundary) if the
     * node still has endpoints; operators must remove endpoints first so the
     * cascade is explicit and audit-visible.
     */
    @Transactional
    public void deleteNode(String nodeId) {
        DirectoryNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new DirectoryLookupException("Node not found: " + nodeId));
        long endpointCount = endpointRepository.countByNodeId(nodeId);
        if (endpointCount > 0) {
            throw new IllegalStateException(
                    "Node " + nodeId + " still has " + endpointCount + " endpoint(s); delete them first");
        }
        String orgId = node.getOrgId();
        nodeRepository.delete(node);
        cacheManager.invalidateOrganization(orgId);
        log.info("Deleted node {} (org={})", nodeId, orgId);
    }

    @Transactional
    public DirectoryEndpoint updateEndpoint(String endpointId, String url, Boolean active) {
        DirectoryEndpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new DirectoryLookupException("Endpoint not found: " + endpointId));
        if (url != null && !url.isBlank()) {
            endpoint.setUrl(url.trim());
        }
        if (active != null) {
            endpoint.setActive(active);
        }
        DirectoryEndpoint saved = endpointRepository.save(endpoint);
        DirectoryNode node = nodeRepository.findById(saved.getNodeId()).orElse(null);
        if (node != null) {
            cacheManager.invalidateOrganization(node.getOrgId());
        }
        log.info("Updated endpoint {} (url={}, active={})", endpointId, saved.getUrl(), saved.isActive());
        return saved;
    }

    private OrganizationDto toDto(DirectoryOrganization org) {
        return OrganizationDto.builder()
                .orgId(org.getOrgId())
                .name(org.getName())
                .oid(org.getOid())
                .orgType(org.getOrgType())
                .active(org.isActive())
                .homeCommunityId(org.getHomeCommunityId())
                .lastSyncedAt(org.getLastSyncedAt())
                .build();
    }
}
