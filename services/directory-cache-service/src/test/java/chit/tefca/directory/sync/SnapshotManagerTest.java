package chit.tefca.directory.sync;

import chit.tefca.directory.dto.DirectorySyncStatus;
import chit.tefca.directory.model.DirectorySnapshot;
import chit.tefca.directory.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotManagerTest {

    @Mock private SnapshotRepository snapshotRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private EndpointRepository endpointRepository;
    @Mock private CapabilityRepository capabilityRepository;

    @InjectMocks
    private SnapshotManager snapshotManager;

    @Test
    void beginSnapshot_createsInProgressSnapshot() {
        when(snapshotRepository.save(any(DirectorySnapshot.class)))
                .thenAnswer(inv -> {
                    DirectorySnapshot s = inv.getArgument(0);
                    s.setSnapshotId(1L);
                    return s;
                });

        DirectorySnapshot snapshot = snapshotManager.beginSnapshot("https://source.example.com");

        assertThat(snapshot.getSnapshotId()).isEqualTo(1L);
        assertThat(snapshot.getStatus()).isEqualTo(DirectorySnapshot.SyncStatus.IN_PROGRESS);
        assertThat(snapshot.getVersionLabel()).startsWith("v-");
        assertThat(snapshot.getSourceUrl()).isEqualTo("https://source.example.com");
        assertThat(snapshot.getStartedAt()).isNotNull();
    }

    @Test
    void completeSnapshot_setsCountsAndCompletedStatus() {
        DirectorySnapshot existing = DirectorySnapshot.builder()
                .snapshotId(1L).versionLabel("v-test")
                .status(DirectorySnapshot.SyncStatus.IN_PROGRESS).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(organizationRepository.count()).thenReturn(5L);
        when(nodeRepository.count()).thenReturn(10L);
        when(endpointRepository.count()).thenReturn(20L);
        when(capabilityRepository.count()).thenReturn(15L);
        when(snapshotRepository.save(any(DirectorySnapshot.class))).thenAnswer(inv -> inv.getArgument(0));

        DirectorySnapshot completed = snapshotManager.completeSnapshot(1L);

        assertThat(completed.getStatus()).isEqualTo(DirectorySnapshot.SyncStatus.COMPLETED);
        assertThat(completed.getOrgCount()).isEqualTo(5);
        assertThat(completed.getNodeCount()).isEqualTo(10);
        assertThat(completed.getEndpointCount()).isEqualTo(20);
        assertThat(completed.getCapabilityCount()).isEqualTo(15);
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    void failSnapshot_setsFailedStatusAndError() {
        DirectorySnapshot existing = DirectorySnapshot.builder()
                .snapshotId(1L).versionLabel("v-test")
                .status(DirectorySnapshot.SyncStatus.IN_PROGRESS).build();
        when(snapshotRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(snapshotRepository.save(any(DirectorySnapshot.class))).thenAnswer(inv -> inv.getArgument(0));

        DirectorySnapshot failed = snapshotManager.failSnapshot(1L, "Connection timeout");

        assertThat(failed.getStatus()).isEqualTo(DirectorySnapshot.SyncStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("Connection timeout");
        assertThat(failed.getCompletedAt()).isNotNull();
    }

    @Test
    void completeSnapshot_throwsIfNotFound() {
        when(snapshotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> snapshotManager.completeSnapshot(99L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Snapshot not found: 99");
    }

    @Test
    void getLatestSyncStatus_returnsNoSync_whenNoneExist() {
        when(snapshotRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.empty());

        DirectorySyncStatus status = snapshotManager.getLatestSyncStatus();

        assertThat(status.getStatus()).isEqualTo("NO_SYNC");
    }

    @Test
    void getLatestSyncStatus_mapsSnapshot() {
        DirectorySnapshot snapshot = DirectorySnapshot.builder()
                .snapshotId(1L).versionLabel("v-test")
                .status(DirectorySnapshot.SyncStatus.COMPLETED)
                .orgCount(5).nodeCount(10).endpointCount(20).capabilityCount(15)
                .build();
        when(snapshotRepository.findTopByOrderByCreatedAtDesc()).thenReturn(Optional.of(snapshot));

        DirectorySyncStatus status = snapshotManager.getLatestSyncStatus();

        assertThat(status.getVersionLabel()).isEqualTo("v-test");
        assertThat(status.getStatus()).isEqualTo("COMPLETED");
        assertThat(status.getOrgCount()).isEqualTo(5);
    }

    @Test
    void getSyncStatus_returnsNull_whenNotFound() {
        when(snapshotRepository.findByVersionLabel("v-missing")).thenReturn(Optional.empty());

        DirectorySyncStatus status = snapshotManager.getSyncStatus("v-missing");

        assertThat(status).isNull();
    }
}
