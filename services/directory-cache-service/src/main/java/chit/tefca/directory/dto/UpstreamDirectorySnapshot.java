package chit.tefca.directory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * The shape of the payload returned by an upstream RCE/QHIN directory feed.
 * Returned by {@code UpstreamDirectoryClient.fetch()} and consumed by
 * {@code DirectorySyncService.syncFromUpstream()}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpstreamDirectorySnapshot {

    /** Vendor/source-provided version label. May be a date, semver, or hash. */
    private String version;

    private List<OrganizationDto> organizations;
    private List<NodeDto> nodes;
    /** Endpoint records — one per (nodeId, modality) combination. */
    private List<UpstreamEndpoint> endpoints;

    public static UpstreamDirectorySnapshot empty() {
        return UpstreamDirectorySnapshot.builder()
                .version("empty")
                .organizations(Collections.emptyList())
                .nodes(Collections.emptyList())
                .endpoints(Collections.emptyList())
                .build();
    }

    public boolean isEmpty() {
        return (organizations == null || organizations.isEmpty())
                && (nodes == null || nodes.isEmpty())
                && (endpoints == null || endpoints.isEmpty());
    }
}
