package chit.tefca.directory.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import chit.tefca.directory.dto.EndpointLookupResponse;
import chit.tefca.directory.dto.NodeDto;
import chit.tefca.directory.dto.OrganizationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the in-process Caffeine-backed directory cache.
 */
class DirectoryCaffeineCacheTest {

    private DirectoryCaffeineCache cache;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        cache = new DirectoryCaffeineCache(objectMapper);
        ReflectionTestUtils.setField(cache, "cacheTtlMinutes", 30);
        ReflectionTestUtils.invokeMethod(cache, "init");
    }

    // ── Organization Cache ────────────────────────────────────────

    @Test
    void getCachedOrganization_hit() {
        OrganizationDto dto = OrganizationDto.builder()
                .orgId("org-1").name("Test Org").active(true).build();
        cache.cacheOrganization("org-1", dto);

        Optional<OrganizationDto> result = cache.getCachedOrganization("org-1");

        assertThat(result).isPresent();
        assertThat(result.get().getOrgId()).isEqualTo("org-1");
        assertThat(result.get().getName()).isEqualTo("Test Org");
    }

    @Test
    void getCachedOrganization_miss() {
        assertThat(cache.getCachedOrganization("missing")).isEmpty();
    }

    // ── Node Cache ────────────────────────────────────────────────

    @Test
    void getCachedNode_hit() {
        NodeDto dto = NodeDto.builder().nodeId("node-1").name("Test Node").build();
        cache.cacheNode("node-1", dto);

        Optional<NodeDto> result = cache.getCachedNode("node-1");
        assertThat(result).isPresent();
        assertThat(result.get().getNodeId()).isEqualTo("node-1");
    }

    @Test
    void getCachedNode_miss() {
        assertThat(cache.getCachedNode("nope")).isEmpty();
    }

    @Test
    void evictNode_removesEntry() {
        cache.cacheNode("node-2", NodeDto.builder().nodeId("node-2").build());
        cache.evictNode("node-2");
        assertThat(cache.getCachedNode("node-2")).isEmpty();
    }

    // ── Endpoint Cache ────────────────────────────────────────────

    @Test
    void getCachedEndpoints_hit() {
        List<EndpointLookupResponse> endpoints = List.of(
                EndpointLookupResponse.builder().endpointId("ep-1").url("https://example.com").build());
        cache.cacheEndpoints("org-1", "QUERY", endpoints);

        Optional<List<EndpointLookupResponse>> result = cache.getCachedEndpoints("org-1", "QUERY");
        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().get(0).getEndpointId()).isEqualTo("ep-1");
    }

    @Test
    void getCachedEndpoints_miss() {
        assertThat(cache.getCachedEndpoints("org-x", "QUERY")).isEmpty();
    }

    // ── Snapshot Version ──────────────────────────────────────────

    @Test
    void snapshot_setAndGetVersion() {
        cache.setCurrentSnapshotVersion("v-20240101-120000");
        assertThat(cache.getCurrentSnapshotVersion()).isEqualTo("v-20240101-120000");
    }

    // ── Invalidation ─────────────────────────────────────────────

    @Test
    void invalidateOrg_removesOrgAndEndpointEntries() {
        cache.cacheOrganization("org-1", OrganizationDto.builder().orgId("org-1").build());
        cache.cacheEndpoints("org-1", "QUERY", List.of());
        cache.cacheEndpoints("org-1", "RETRIEVE", List.of());
        cache.cacheOrganization("org-2", OrganizationDto.builder().orgId("org-2").build());

        cache.invalidateOrg("org-1");

        assertThat(cache.getCachedOrganization("org-1")).isEmpty();
        assertThat(cache.getCachedEndpoints("org-1", "QUERY")).isEmpty();
        assertThat(cache.getCachedEndpoints("org-1", "RETRIEVE")).isEmpty();
        // Other orgs untouched
        assertThat(cache.getCachedOrganization("org-2")).isPresent();
    }

    @Test
    void invalidateAll_removesAllEntries() {
        cache.cacheOrganization("org-a", OrganizationDto.builder().orgId("org-a").build());
        cache.cacheNode("node-a", NodeDto.builder().nodeId("node-a").build());

        cache.invalidateAll();

        assertThat(cache.getCachedOrganization("org-a")).isEmpty();
        assertThat(cache.getCachedNode("node-a")).isEmpty();
    }
}
