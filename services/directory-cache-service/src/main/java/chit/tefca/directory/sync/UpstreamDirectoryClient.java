package chit.tefca.directory.sync;

import chit.tefca.directory.dto.UpstreamDirectorySnapshot;

/**
 * Fetches directory data from the upstream RCE/QHIN national directory.
 * Implementations may use HTTP, gRPC, or file-based ingestion. The default
 * implementation is {@link WebClientUpstreamDirectoryClient}.
 */
public interface UpstreamDirectoryClient {

    /**
     * Pulls a full directory snapshot from the configured upstream source.
     * When no upstream is configured (e.g. local dev) implementations should
     * return an empty snapshot rather than throwing — this lets the sync
     * job complete and operators can seed data manually.
     *
     * @return parsed snapshot containing organizations, nodes, endpoints, capabilities
     */
    UpstreamDirectorySnapshot fetch();
}
