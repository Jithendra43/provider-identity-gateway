package chit.tefca.directory.sync;

import chit.tefca.directory.cache.DirectoryCacheManager;
import chit.tefca.directory.config.DirectoryProperties;
import chit.tefca.directory.dto.UpstreamDirectorySnapshot;
import chit.tefca.directory.model.DirectoryOrganization;
import chit.tefca.directory.model.DirectorySnapshot;
import chit.tefca.directory.repository.EndpointRepository;
import chit.tefca.directory.repository.NodeRepository;
import chit.tefca.directory.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectorySyncServiceTest {

    @Mock private SnapshotManager snapshotManager;
    @Mock private DirectoryCacheManager cacheManager;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private EndpointRepository endpointRepository;
    @Mock private UpstreamDirectoryClient upstreamClient;
    @Mock private DirectoryProperties properties;

    @InjectMocks
    private DirectorySyncService syncService;

    @Test
    void syncFromUpstream_completesSuccessfully() {
        when(properties.getSourceUrl()).thenReturn("https://rce.example.com/directory");
        when(upstreamClient.fetch()).thenReturn(UpstreamDirectorySnapshot.empty());

        DirectorySnapshot inProgress = DirectorySnapshot.builder()
                .snapshotId(1L).versionLabel("v-test")
                .status(DirectorySnapshot.SyncStatus.IN_PROGRESS).build();
        when(snapshotManager.beginSnapshot(anyString())).thenReturn(inProgress);

        DirectoryOrganization org = DirectoryOrganization.builder().orgId("org-1").name("Org 1").build();
        when(organizationRepository.findAll()).thenReturn(List.of(org));

        DirectorySnapshot completed = DirectorySnapshot.builder()
                .snapshotId(1L).versionLabel("v-test")
                .status(DirectorySnapshot.SyncStatus.COMPLETED).build();
        when(snapshotManager.completeSnapshot(1L)).thenReturn(completed);

        DirectorySnapshot result = syncService.syncFromUpstream();

        assertThat(result.getStatus()).isEqualTo(DirectorySnapshot.SyncStatus.COMPLETED);
        verify(cacheManager).invalidateAll();
        verify(cacheManager).setCurrentSnapshotVersion("v-test");
        verify(organizationRepository).save(org);
    }

    @Test
    void syncFromUpstream_handlesFailure() {
        when(properties.getSourceUrl()).thenReturn("https://rce.example.com/directory");
        when(upstreamClient.fetch()).thenReturn(UpstreamDirectorySnapshot.empty());

        DirectorySnapshot inProgress = DirectorySnapshot.builder()
                .snapshotId(1L).versionLabel("v-test")
                .status(DirectorySnapshot.SyncStatus.IN_PROGRESS).build();
        when(snapshotManager.beginSnapshot(anyString())).thenReturn(inProgress);

        when(organizationRepository.findAll()).thenThrow(new RuntimeException("DB down"));
        when(snapshotManager.failSnapshot(anyLong(), anyString()))
                .thenReturn(DirectorySnapshot.builder().status(DirectorySnapshot.SyncStatus.FAILED).build());

        assertThatThrownBy(() -> syncService.syncFromUpstream())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Directory sync failed");

        verify(snapshotManager).failSnapshot(eq(1L), anyString());
    }
}
