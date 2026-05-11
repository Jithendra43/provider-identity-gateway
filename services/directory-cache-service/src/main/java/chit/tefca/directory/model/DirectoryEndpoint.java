package chit.tefca.directory.model;

import chit.tefca.common.enums.Modality;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity(name = "DirectoryEndpoint_DirectoryCache")
@Table(name = "directory_endpoints", schema = "directory")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "endpointId")
@ToString(exclude = "node")
public class DirectoryEndpoint {

    @Id
    @Column(name = "endpoint_id", length = 64)
    private String endpointId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false, insertable = false, updatable = false)
    private DirectoryNode node;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Modality modality;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "certificate_alias")
    private String certificateAlias;

    @Column(name = "supported_operations", length = 512)
    private String supportedOperations;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Column(name = "health_check_url", length = 512)
    private String healthCheckUrl;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
