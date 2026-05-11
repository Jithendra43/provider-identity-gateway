package chit.tefca.directory.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import chit.tefca.directory.dto.EndpointLookupResponse;
import chit.tefca.directory.dto.NodeDto;
import chit.tefca.directory.dto.OrganizationDto;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-process directory cache backed by Caffeine. The TEFCA gateway runs as a
 * single consolidated fat-jar ({@code tefca-gateway-app}), so a distributed
 * cache (Redis / ElastiCache) is unnecessary and would only add network
 * latency to the 500&nbsp;ms PA hot path.
 *
 * <p>Backed by Caffeine: 30-minute TTL after write, 50,000-entry maximum,
 * holds live Java references (no JSON round-trip per get/put).</p>
 *
 * <p>The Jackson {@code ObjectMapper} dependency is retained as a constructor
 * parameter for backwards compatibility with earlier wiring; it is not used
 * by Caffeine itself.</p>
 */
@Component
public class DirectoryCaffeineCache {

    private static final Logger log = LoggerFactory.getLogger(DirectoryCaffeineCache.class);

    @SuppressWarnings("unused")
    private final ObjectMapper objectMapper;

    @Value("${tefca.directory.cache-ttl-minutes:30}")
    private int cacheTtlMinutes;

    private Cache<String, Object> cache;
    private final ConcurrentMap<String, String> snapshotVersion = new ConcurrentHashMap<>();

    public DirectoryCaffeineCache(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(cacheTtlMinutes))
                .maximumSize(50_000)
                .build();
    }

    // ── Endpoint Cache ────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Optional<List<EndpointLookupResponse>> getCachedEndpoints(String orgId, String modality) {
        Object v = cache.getIfPresent(CacheKeyDesign.endpointsKey(orgId, modality));
        return Optional.ofNullable((List<EndpointLookupResponse>) v);
    }

    public void cacheEndpoints(String orgId, String modality, List<EndpointLookupResponse> endpoints) {
        cache.put(CacheKeyDesign.endpointsKey(orgId, modality), endpoints);
    }

    // ── Organization Cache ────────────────────────────────────────

    public Optional<OrganizationDto> getCachedOrganization(String orgId) {
        Object v = cache.getIfPresent(CacheKeyDesign.orgKey(orgId));
        return Optional.ofNullable((OrganizationDto) v);
    }

    public void cacheOrganization(String orgId, OrganizationDto org) {
        cache.put(CacheKeyDesign.orgKey(orgId), org);
    }

    // ── Node Cache ────────────────────────────────────────────────

    public Optional<NodeDto> getCachedNode(String nodeId) {
        Object v = cache.getIfPresent(CacheKeyDesign.nodeKey(nodeId));
        return Optional.ofNullable((NodeDto) v);
    }

    public void cacheNode(String nodeId, NodeDto node) {
        cache.put(CacheKeyDesign.nodeKey(nodeId), node);
    }

    public void evictNode(String nodeId) {
        cache.invalidate(CacheKeyDesign.nodeKey(nodeId));
    }

    // ── Capabilities Cache ────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Optional<List<String>> getCachedCapabilities(String nodeId) {
        Object v = cache.getIfPresent(CacheKeyDesign.capabilitiesKey(nodeId));
        return Optional.ofNullable((List<String>) v);
    }

    public void cacheCapabilities(String nodeId, List<String> capabilities) {
        cache.put(CacheKeyDesign.capabilitiesKey(nodeId), capabilities);
    }

    public void evictCapabilities(String nodeId) {
        cache.invalidate(CacheKeyDesign.capabilitiesKey(nodeId));
    }

    // ── Snapshot Version ──────────────────────────────────────────

    public String getCurrentSnapshotVersion() {
        return snapshotVersion.get(CacheKeyDesign.snapshotCurrentKey());
    }

    public void setCurrentSnapshotVersion(String versionLabel) {
        snapshotVersion.put(CacheKeyDesign.snapshotCurrentKey(), versionLabel);
    }

    // ── Invalidation ─────────────────────────────────────────────

    public void invalidateOrg(String orgId) {
        String prefix = "dir:endpoints:" + orgId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        cache.invalidate(CacheKeyDesign.orgKey(orgId));
        log.info("Invalidated cache for orgId={}", orgId);
    }

    public void invalidateAll() {
        long size = cache.estimatedSize();
        cache.invalidateAll();
        log.info("Invalidated ~{} directory cache keys", size);
    }
}
