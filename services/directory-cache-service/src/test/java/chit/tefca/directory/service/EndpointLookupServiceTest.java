package chit.tefca.directory.service;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.exception.DirectoryLookupException;
import chit.tefca.directory.cache.DirectoryCaffeineCache;
import chit.tefca.directory.dto.EndpointLookupRequest;
import chit.tefca.directory.dto.EndpointLookupResponse;
import chit.tefca.directory.model.DirectoryEndpoint;
import chit.tefca.directory.model.DirectoryNode;
import chit.tefca.directory.repository.EndpointRepository;
import chit.tefca.directory.repository.NodeRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndpointLookupServiceTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private EndpointRepository endpointRepository;
    @Mock private DirectoryCaffeineCache caffeineCache;

    private MeterRegistry meterRegistry;
    private EndpointLookupService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new EndpointLookupService(nodeRepository, endpointRepository, caffeineCache, meterRegistry);
    }

    @Test
    void lookupEndpoints_cacheHit_returnsCachedResults() {
        EndpointLookupRequest request = EndpointLookupRequest.builder()
                .targetOrgId("org-1")
                .modality(Modality.XCA_QUERY)
                .build();
        List<EndpointLookupResponse> cached = List.of(
                EndpointLookupResponse.builder().endpointId("ep-1").build());
        when(caffeineCache.getCachedEndpoints("org-1", "XCA_QUERY")).thenReturn(Optional.of(cached));

        List<EndpointLookupResponse> result = service.lookupEndpoints(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEndpointId()).isEqualTo("ep-1");
        // Verify database was not queried
        verifyNoInteractions(endpointRepository);
        // Verify hit counter
        Counter hitCounter = meterRegistry.find("directory.cache.hit").counter();
        assertThat(hitCounter).isNotNull();
        assertThat(hitCounter.count()).isEqualTo(1.0);
    }

    @Test
    void lookupEndpoints_cacheMiss_queriesDatabase() {
        EndpointLookupRequest request = EndpointLookupRequest.builder()
                .targetOrgId("org-1")
                .build();
        when(caffeineCache.getCachedEndpoints("org-1", null)).thenReturn(Optional.empty());

        DirectoryEndpoint ep = DirectoryEndpoint.builder()
                .endpointId("ep-1").nodeId("node-1").url("https://ep.example.com")
                .modality(Modality.XCA_QUERY).active(true).build();
        when(endpointRepository.findActiveByOrgId("org-1")).thenReturn(List.of(ep));

        List<EndpointLookupResponse> result = service.lookupEndpoints(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUrl()).isEqualTo("https://ep.example.com");
        // Verify cache was populated
        verify(caffeineCache).cacheEndpoints(eq("org-1"), isNull(), anyList());
        // Verify miss counter
        Counter missCounter = meterRegistry.find("directory.cache.miss").counter();
        assertThat(missCounter).isNotNull();
        assertThat(missCounter.count()).isEqualTo(1.0);
    }

    @Test
    void lookupEndpoints_noOrgOrNodeId_throwsException() {
        EndpointLookupRequest request = EndpointLookupRequest.builder().build();

        assertThatThrownBy(() -> service.lookupEndpoints(request))
                .isInstanceOf(DirectoryLookupException.class)
                .hasMessageContaining("targetOrgId or targetNodeId");
    }

    @Test
    void lookupEndpoints_byNodeId_resolvesNode() {
        EndpointLookupRequest request = EndpointLookupRequest.builder()
                .targetNodeId("node-1")
                .build();
        when(caffeineCache.getCachedEndpoints(isNull(), isNull())).thenReturn(Optional.empty());

        DirectoryNode node = DirectoryNode.builder()
                .nodeId("node-1").orgId("org-1").name("Node 1").build();
        when(nodeRepository.findById("node-1")).thenReturn(Optional.of(node));

        DirectoryEndpoint ep = DirectoryEndpoint.builder()
                .endpointId("ep-1").nodeId("node-1").url("https://ep.example.com")
                .modality(Modality.XCA_QUERY).active(true).build();
        when(endpointRepository.findByNodeIdAndActiveTrue("node-1")).thenReturn(List.of(ep));

        List<EndpointLookupResponse> result = service.lookupEndpoints(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrgId()).isEqualTo("org-1");
    }

    @Test
    void getEndpointsForOrg_delegatesToLookup() {
        when(caffeineCache.getCachedEndpoints("org-1", null)).thenReturn(Optional.empty());
        when(endpointRepository.findActiveByOrgId("org-1")).thenReturn(List.of());

        List<EndpointLookupResponse> result = service.getEndpointsForOrg("org-1");

        assertThat(result).isEmpty();
    }
}
