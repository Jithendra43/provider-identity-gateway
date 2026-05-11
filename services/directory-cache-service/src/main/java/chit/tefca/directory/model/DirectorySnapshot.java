package chit.tefca.directory.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "directory_snapshots", schema = "directory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectorySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "version_label", nullable = false, unique = true, length = 64)
    private String versionLabel;

    @Column(name = "org_count", nullable = false)
    private int orgCount;

    @Column(name = "node_count", nullable = false)
    private int nodeCount;

    @Column(name = "endpoint_count", nullable = false)
    private int endpointCount;

    @Column(name = "capability_count", nullable = false)
    private int capabilityCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SyncStatus status;

    @Column(name = "source_url", length = 512)
    private String sourceUrl;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public enum SyncStatus {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
