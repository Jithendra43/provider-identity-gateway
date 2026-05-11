package chit.tefca.directory.sync;

import chit.tefca.directory.dto.DirectorySyncStatus;
import chit.tefca.directory.model.DirectorySnapshot;
import chit.tefca.directory.repository.CapabilityRepository;
import chit.tefca.directory.repository.EndpointRepository;
import chit.tefca.directory.repository.NodeRepository;
import chit.tefca.directory.repository.OrganizationRepository;
import chit.tefca.directory.repository.SnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class SnapshotManager {

    private static final Logger log = LoggerFactory.getLogger(SnapshotManager.class);
    private static final DateTimeFormatter VERSION_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final SnapshotRepository snapshotRepository;
    private final OrganizationRepository organizationRepository;
    private final NodeRepository nodeRepository;
    private final EndpointRepository endpointRepository;
    private final CapabilityRepository capabilityRepository;

    @Transactional
    public DirectorySnapshot beginSnapshot(String sourceUrl) {
        String versionLabel = "v-" + VERSION_FORMAT.format(Instant.now());
        DirectorySnapshot snapshot = DirectorySnapshot.builder()
                .versionLabel(versionLabel)
                .status(DirectorySnapshot.SyncStatus.IN_PROGRESS)
                .sourceUrl(sourceUrl)
                .startedAt(Instant.now())
                .orgCount(0)
                .nodeCount(0)
                .endpointCount(0)
                .capabilityCount(0)
                .build();
        return snapshotRepository.save(snapshot);
    }

    @Transactional
    public DirectorySnapshot completeSnapshot(Long snapshotId) {
        DirectorySnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalStateException("Snapshot not found: " + snapshotId));

        snapshot.setOrgCount((int) organizationRepository.count());
        snapshot.setNodeCount((int) nodeRepository.count());
        snapshot.setEndpointCount((int) endpointRepository.count());
        snapshot.setCapabilityCount((int) capabilityRepository.count());
        snapshot.setStatus(DirectorySnapshot.SyncStatus.COMPLETED);
        snapshot.setCompletedAt(Instant.now());

        log.info("Snapshot {} completed: orgs={}, nodes={}, endpoints={}, capabilities={}",
                snapshot.getVersionLabel(), snapshot.getOrgCount(), snapshot.getNodeCount(),
                snapshot.getEndpointCount(), snapshot.getCapabilityCount());

        return snapshotRepository.save(snapshot);
    }

    @Transactional
    public DirectorySnapshot failSnapshot(Long snapshotId, String errorMessage) {
        DirectorySnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalStateException("Snapshot not found: " + snapshotId));

        snapshot.setStatus(DirectorySnapshot.SyncStatus.FAILED);
        snapshot.setErrorMessage(errorMessage);
        snapshot.setCompletedAt(Instant.now());

        log.error("Snapshot {} failed: {}", snapshot.getVersionLabel(), errorMessage);
        return snapshotRepository.save(snapshot);
    }

    public DirectorySyncStatus getLatestSyncStatus() {
        return snapshotRepository.findTopByOrderByCreatedAtDesc()
                .map(this::toSyncStatus)
                .orElse(DirectorySyncStatus.builder()
                        .status("NO_SYNC")
                        .build());
    }

    public DirectorySyncStatus getSyncStatus(String versionLabel) {
        return snapshotRepository.findByVersionLabel(versionLabel)
                .map(this::toSyncStatus)
                .orElse(null);
    }

    private DirectorySyncStatus toSyncStatus(DirectorySnapshot s) {
        return DirectorySyncStatus.builder()
                .snapshotId(s.getSnapshotId())
                .versionLabel(s.getVersionLabel())
                .status(s.getStatus().name())
                .orgCount(s.getOrgCount())
                .nodeCount(s.getNodeCount())
                .endpointCount(s.getEndpointCount())
                .capabilityCount(s.getCapabilityCount())
                .sourceUrl(s.getSourceUrl())
                .errorMessage(s.getErrorMessage())
                .startedAt(s.getStartedAt())
                .completedAt(s.getCompletedAt())
                .build();
    }
}
