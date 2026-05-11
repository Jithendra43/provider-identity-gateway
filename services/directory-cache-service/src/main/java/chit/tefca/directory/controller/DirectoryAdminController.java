package chit.tefca.directory.controller;

import chit.tefca.common.enums.Modality;
import chit.tefca.directory.cache.DirectoryCacheManager;
import chit.tefca.directory.dto.DirectorySyncStatus;
import chit.tefca.directory.model.DirectoryEndpoint;
import chit.tefca.directory.service.DirectoryAdminService;
import chit.tefca.directory.sync.DirectorySyncService;
import chit.tefca.directory.sync.SnapshotManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/directory")
@RequiredArgsConstructor
public class DirectoryAdminController {

    private final DirectorySyncService directorySyncService;
    private final DirectoryCacheManager cacheManager;
    private final SnapshotManager snapshotManager;
    private final DirectoryAdminService directoryAdminService;

    @PostMapping("/sync")
    public ResponseEntity<DirectorySyncStatus> triggerSync() {
        directorySyncService.syncFromUpstream();
        return ResponseEntity.ok(snapshotManager.getLatestSyncStatus());
    }

    @GetMapping("/sync/status")
    public ResponseEntity<DirectorySyncStatus> getSyncStatus() {
        return ResponseEntity.ok(snapshotManager.getLatestSyncStatus());
    }

    @GetMapping("/sync/status/{versionLabel}")
    public ResponseEntity<DirectorySyncStatus> getSyncStatusByVersion(@PathVariable String versionLabel) {
        DirectorySyncStatus status = snapshotManager.getSyncStatus(versionLabel);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    @PostMapping("/cache/invalidate")
    public ResponseEntity<Map<String, String>> invalidateAllCache() {
        cacheManager.invalidateAll();
        return ResponseEntity.ok(Map.of("status", "ALL_CACHE_INVALIDATED"));
    }

    @PostMapping("/cache/invalidate/{orgId}")
    public ResponseEntity<Map<String, String>> invalidateOrgCache(@PathVariable String orgId) {
        cacheManager.invalidateOrganization(orgId);
        return ResponseEntity.ok(Map.of("status", "ORG_CACHE_INVALIDATED", "orgId", orgId));
    }

    @GetMapping("/cache/version")
    public ResponseEntity<Map<String, String>> getCacheVersion() {
        String version = cacheManager.getCurrentSnapshotVersion();
        return ResponseEntity.ok(Map.of("currentVersion", version != null ? version : "NONE"));
    }

    /**
     * Patch a directory endpoint's mutable fields (currently {@code url} and
     * {@code active}). Used by the admin Directory page so operators can re-point
     * a registered endpoint at a different downstream host without applying a
     * SQL migration. Triggers a per-org cache invalidation so routing picks up
     * the new value on the next lookup.
     */
    @PatchMapping("/endpoints/{endpointId}")
    public ResponseEntity<DirectoryEndpoint> updateEndpoint(@PathVariable String endpointId,
                                                            @RequestBody Map<String, Object> body) {
        Object urlVal = body.get("url");
        Object activeVal = body.get("active");
        DirectoryEndpoint updated = directoryAdminService.updateEndpoint(
                endpointId,
                urlVal != null ? urlVal.toString() : null,
                activeVal instanceof Boolean b ? b : null
        );
        return ResponseEntity.ok(updated);
    }

    /**
     * Create a new directory endpoint. Operators add endpoints from the admin
     * UI when onboarding a new partner host without a full RCE sync. Body:
     * <pre>{ "endpointId", "nodeId", "url", "modality", "active"?,
     *        "supportedOperations"?, "timeoutMs"?, "healthCheckUrl"?,
     *        "certificateAlias"? }</pre>
     */
    @PostMapping("/endpoints")
    public ResponseEntity<DirectoryEndpoint> createEndpoint(@RequestBody Map<String, Object> body) {
        String endpointId = requireString(body, "endpointId");
        String nodeId     = requireString(body, "nodeId");
        String url        = requireString(body, "url");
        String modality   = requireString(body, "modality");

        try {
            URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid url: " + url);
        }
        Modality modalityEnum;
        try {
            modalityEnum = Modality.valueOf(modality);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid modality: " + modality);
        }

        Object activeVal  = body.get("active");
        Object timeoutVal = body.get("timeoutMs");
        boolean activeFlag = !(activeVal instanceof Boolean b) || b;

        DirectoryEndpoint endpoint = DirectoryEndpoint.builder()
                .endpointId(endpointId)
                .nodeId(nodeId)
                .url(url)
                .modality(modalityEnum)
                .active(activeFlag)
                .supportedOperations(asString(body.get("supportedOperations")))
                .certificateAlias(asString(body.get("certificateAlias")))
                .healthCheckUrl(asString(body.get("healthCheckUrl")))
                .timeoutMs(timeoutVal instanceof Number n ? n.intValue() : null)
                .build();

        directoryAdminService.createEndpoint(endpoint);
        return ResponseEntity.status(HttpStatus.CREATED).body(endpoint);
    }

    @DeleteMapping("/endpoints/{endpointId}")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable String endpointId) {
        directoryAdminService.deleteEndpoint(endpointId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete a node. Returns 409 CONFLICT if the node still has endpoints
     * (the service throws {@code IllegalStateException} in that case) so the
     * UI can surface a precise message instead of a silent cascade.
     */
    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<Void> deleteNode(@PathVariable String nodeId) {
        try {
            directoryAdminService.deleteNode(nodeId);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    private static String requireString(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required field: " + key);
        }
        return v.toString().trim();
    }

    private static String asString(Object v) {
        return (v == null || v.toString().isBlank()) ? null : v.toString().trim();
    }
}
