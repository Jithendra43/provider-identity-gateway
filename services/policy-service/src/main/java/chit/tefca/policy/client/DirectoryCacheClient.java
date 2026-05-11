package chit.tefca.policy.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * REST client for the directory-cache-service.
 * Used by policy validators to verify org existence and status.
 */
@Component("policyDirectoryCacheClient")
public class DirectoryCacheClient {

    private static final Logger log = LoggerFactory.getLogger(DirectoryCacheClient.class);

    private final RestClient restClient;

    public DirectoryCacheClient(
            @Value("${tefca.directory-cache.base-url:http://localhost:8083}") String baseUrl,
            RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    /**
     * Returns true if the organization exists and is active in the directory.
     */
    @SuppressWarnings("unchecked")
    public boolean isOrganizationActive(String orgId) {
        try {
            Map<String, Object> org = restClient.get()
                    .uri("/api/v1/directory/organizations/{orgId}", orgId)
                    .retrieve()
                    .body(Map.class);
            if (org == null) {
                return false;
            }
            Object active = org.get("active");
            return Boolean.TRUE.equals(active);
        } catch (Exception e) {
            log.warn("Directory cache lookup failed for org {}: {}", orgId, e.getMessage());
            throw e;
        }
    }
}
