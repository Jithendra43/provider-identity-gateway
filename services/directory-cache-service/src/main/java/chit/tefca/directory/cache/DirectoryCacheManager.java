package chit.tefca.directory.cache;

import chit.tefca.directory.dto.OrganizationDto;
import org.springframework.stereotype.Component;

/**
 * Facade that coordinates all directory cache operations.
 * Delegates to DirectoryCaffeineCache for actual cache interactions and provides
 * higher-level operations like full-invalidation and warm-up.
 */
@Component
public class DirectoryCacheManager {

    private final DirectoryCaffeineCache caffeineCache;

    public DirectoryCacheManager(DirectoryCaffeineCache caffeineCache) {
        this.caffeineCache = caffeineCache;
    }

    public void invalidateOrganization(String orgId) {
        caffeineCache.invalidateOrg(orgId);
    }

    public void invalidateNode(String nodeId) {
        caffeineCache.evictNode(nodeId);
        caffeineCache.evictCapabilities(nodeId);
    }

    public void invalidateAll() {
        caffeineCache.invalidateAll();
    }

    public void cacheOrganization(String orgId, OrganizationDto dto) {
        caffeineCache.cacheOrganization(orgId, dto);
    }

    public void setCurrentSnapshotVersion(String versionLabel) {
        caffeineCache.setCurrentSnapshotVersion(versionLabel);
    }

    public String getCurrentSnapshotVersion() {
        return caffeineCache.getCurrentSnapshotVersion();
    }
}
