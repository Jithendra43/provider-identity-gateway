package chit.tefca.directory.sync;

import chit.tefca.directory.cache.DirectoryCacheManager;
import chit.tefca.directory.config.DirectoryProperties;
import chit.tefca.directory.dto.OrganizationDto;
import chit.tefca.directory.dto.NodeDto;
import chit.tefca.directory.dto.UpstreamDirectorySnapshot;
import chit.tefca.directory.dto.UpstreamEndpoint;
import chit.tefca.directory.model.DirectoryEndpoint;
import chit.tefca.directory.model.DirectoryNode;
import chit.tefca.directory.model.DirectoryOrganization;
import chit.tefca.directory.model.DirectorySnapshot;
import chit.tefca.directory.repository.EndpointRepository;
import chit.tefca.directory.repository.NodeRepository;
import chit.tefca.directory.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Handles the actual directory synchronization logic.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Begin a snapshot record (audit trail).</li>
 *   <li>Fetch the upstream snapshot via {@link UpstreamDirectoryClient}.</li>
 *   <li>Upsert organizations, nodes, and endpoints into PostgreSQL.</li>
 *   <li>Invalidate the Redis cache so subsequent reads see fresh data.</li>
 *   <li>Mark the snapshot complete; record the version label as the current cache snapshot.</li>
 * </ol>
 *
 * <p>If no upstream URL is configured, the upstream client returns an empty
 * snapshot and the existing rows are simply touched ({@code lastSyncedAt}) — the
 * sync is treated as successful so the cache version pointer still advances.
 */
@Service
@RequiredArgsConstructor
public class DirectorySyncService {

    private static final Logger log = LoggerFactory.getLogger(DirectorySyncService.class);

    private final SnapshotManager snapshotManager;
    private final DirectoryCacheManager cacheManager;
    private final OrganizationRepository organizationRepository;
    private final NodeRepository nodeRepository;
    private final EndpointRepository endpointRepository;
    private final UpstreamDirectoryClient upstreamClient;
    private final DirectoryProperties properties;

    /**
     * Performs a full directory sync from the upstream TEFCA directory.
     */
    @Transactional
    public DirectorySnapshot syncFromUpstream() {
        String sourceUrl = properties.getSourceUrl();
        log.info("Starting directory sync from source={}", sourceUrl);

        DirectorySnapshot snapshot = snapshotManager.beginSnapshot(sourceUrl);

        try {
            // Step 1: Fetch upstream snapshot. May be empty when no source is configured.
            UpstreamDirectorySnapshot upstream = upstreamClient.fetch();

            // Step 2: Upsert. When upstream is empty, the loops are no-ops and we fall
            // through to refreshing the lastSyncedAt timestamp on existing orgs (legacy
            // behavior preserved for local/dev profiles).
            int orgs = upsertOrganizations(upstream);
            int nodes = upsertNodes(upstream);
            int endpoints = upsertEndpoints(upstream);

            if (upstream.isEmpty()) {
                organizationRepository.findAll().forEach(org -> {
                    org.setLastSyncedAt(Instant.now());
                    organizationRepository.save(org);
                });
                log.info("Upstream snapshot was empty; refreshed lastSyncedAt on existing orgs");
            } else {
                log.info("Persisted upstream snapshot version={}: orgs={} nodes={} endpoints={}",
                        upstream.getVersion(), orgs, nodes, endpoints);
            }

            // Step 3: Invalidate Redis cache so next lookups fetch fresh data
            cacheManager.invalidateAll();

            // Step 4: Complete the snapshot with current counts
            DirectorySnapshot completed = snapshotManager.completeSnapshot(snapshot.getSnapshotId());
            cacheManager.setCurrentSnapshotVersion(completed.getVersionLabel());

            log.info("Directory sync completed. snapshot={}", completed.getVersionLabel());
            return completed;

        } catch (Exception e) {
            log.error("Directory sync failed for snapshot={}", snapshot.getVersionLabel(), e);
            snapshotManager.failSnapshot(snapshot.getSnapshotId(), e.getMessage());
            throw new RuntimeException("Directory sync failed", e);
        }
    }

    public void syncNow() {
        syncFromUpstream();
    }

    private int upsertOrganizations(UpstreamDirectorySnapshot upstream) {
        List<OrganizationDto> incoming = upstream.getOrganizations();
        if (incoming == null || incoming.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        for (OrganizationDto dto : incoming) {
            DirectoryOrganization org = organizationRepository.findById(dto.getOrgId())
                    .orElseGet(() -> DirectoryOrganization.builder().orgId(dto.getOrgId()).build());
            org.setName(dto.getName());
            org.setOid(dto.getOid());
            org.setOrgType(dto.getOrgType());
            org.setActive(dto.isActive());
            org.setHomeCommunityId(dto.getHomeCommunityId());
            org.setLastSyncedAt(now);
            organizationRepository.save(org);
        }
        return incoming.size();
    }

    private int upsertNodes(UpstreamDirectorySnapshot upstream) {
        List<NodeDto> incoming = upstream.getNodes();
        if (incoming == null || incoming.isEmpty()) {
            return 0;
        }
        for (NodeDto dto : incoming) {
            DirectoryNode node = nodeRepository.findById(dto.getNodeId())
                    .orElseGet(() -> DirectoryNode.builder().nodeId(dto.getNodeId()).build());
            node.setOrgId(dto.getOrgId());
            node.setName(dto.getName());
            node.setHomeCommunityId(dto.getHomeCommunityId());
            node.setStatus(dto.getStatus());
            nodeRepository.save(node);
        }
        return incoming.size();
    }

    private int upsertEndpoints(UpstreamDirectorySnapshot upstream) {
        List<UpstreamEndpoint> incoming = upstream.getEndpoints();
        if (incoming == null || incoming.isEmpty()) {
            return 0;
        }
        for (UpstreamEndpoint dto : incoming) {
            DirectoryEndpoint endpoint = endpointRepository.findById(dto.getEndpointId())
                    .orElseGet(() -> DirectoryEndpoint.builder().endpointId(dto.getEndpointId()).build());
            endpoint.setNodeId(dto.getNodeId());
            endpoint.setUrl(dto.getUrl());
            endpoint.setModality(dto.getModality());
            endpoint.setActive(dto.isActive());
            endpoint.setCertificateAlias(dto.getCertificateAlias());
            endpoint.setSupportedOperations(dto.getSupportedOperations());
            endpointRepository.save(endpoint);
        }
        return incoming.size();
    }
}
