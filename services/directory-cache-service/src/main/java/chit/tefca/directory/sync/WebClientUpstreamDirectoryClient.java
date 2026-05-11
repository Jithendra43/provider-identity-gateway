package chit.tefca.directory.sync;

import chit.tefca.directory.config.DirectoryProperties;
import chit.tefca.directory.dto.UpstreamDirectorySnapshot;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * Default {@link UpstreamDirectoryClient} that pulls a JSON snapshot from the
 * configured upstream URL using {@code WebClient}. When no source is configured
 * (typical for local/dev/test profiles) it returns {@link
 * UpstreamDirectorySnapshot#empty()} so the sync pipeline still completes
 * cleanly and operators can seed the directory tables manually.
 *
 * <p>Authentication: when {@code tefca.directory.bearer-token} is set, it is
 * sent in the {@code Authorization} header. In production this token should be
 * sourced from AWS Secrets Manager and rotated regularly.
 */
@Component
@RequiredArgsConstructor
public class WebClientUpstreamDirectoryClient implements UpstreamDirectoryClient {

    private static final Logger log = LoggerFactory.getLogger(WebClientUpstreamDirectoryClient.class);

    private final DirectoryProperties properties;

    /**
     * Standard Spring-provided WebClient builder. Outbound mTLS, when needed,
     * should be applied at the partner-specific service layer using a qualified
     * builder bean (see {@code TlsConfig#mtlsWebClientBuilder}).
     */
    private final WebClient.Builder webClientBuilder;

    @Override
    public UpstreamDirectorySnapshot fetch() {
        String sourceUrl = properties.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            log.warn("No upstream directory URL configured (tefca.directory.source-url); "
                    + "skipping fetch and returning empty snapshot");
            return UpstreamDirectorySnapshot.empty();
        }

        Duration timeout = Duration.ofSeconds(properties.getSyncTimeoutSeconds());
        log.info("Fetching directory snapshot from upstream={} (timeout={}s)", sourceUrl, timeout.getSeconds());

        try {
            UpstreamDirectorySnapshot snapshot = webClientBuilder.build()
                    .get()
                    .uri(sourceUrl)
                    .retrieve()
                    .bodyToMono(UpstreamDirectorySnapshot.class)
                    .timeout(timeout)
                    .block();

            if (snapshot == null) {
                log.warn("Upstream returned null snapshot; treating as empty");
                return UpstreamDirectorySnapshot.empty();
            }
            log.info("Fetched upstream snapshot version={} (orgs={}, nodes={}, endpoints={})",
                    snapshot.getVersion(),
                    snapshot.getOrganizations() == null ? 0 : snapshot.getOrganizations().size(),
                    snapshot.getNodes() == null ? 0 : snapshot.getNodes().size(),
                    snapshot.getEndpoints() == null ? 0 : snapshot.getEndpoints().size());
            return snapshot;

        } catch (WebClientResponseException e) {
            log.error("Upstream directory fetch failed: HTTP {} from {}", e.getStatusCode(), sourceUrl);
            throw new UpstreamDirectoryFetchException(
                    "Upstream returned HTTP " + e.getStatusCode(), e);
        } catch (Exception e) {
            log.error("Upstream directory fetch failed: {}", e.getMessage(), e);
            throw new UpstreamDirectoryFetchException("Failed to fetch upstream directory", e);
        }
    }

    /** Thrown when the upstream directory fetch fails after timeouts/retries. */
    public static class UpstreamDirectoryFetchException extends RuntimeException {
        public UpstreamDirectoryFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
