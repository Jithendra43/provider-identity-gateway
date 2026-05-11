package chit.tefca.ingress.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA view of {@code directory.directory_endpoints} from the ingress side.
 *
 * <p>The directory schema is owned by the directory-cache-service, which
 * synchronises rows from the RCE directory snapshot. This entity exists in
 * the ingress module solely so the partner onboarding API can insert / mark
 * inactive endpoint rows that correspond to a locally-onboarded partner —
 * those rows are linked back to the partner via the nullable
 * {@code partner_id} column added in V008.</p>
 */
@Entity(name = "DirectoryEndpoint_Ingress")
@Table(name = "directory_endpoints", schema = "directory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectoryEndpoint {

    @Id
    @Column(name = "endpoint_id", length = 64)
    private String endpointId;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Column(name = "partner_id", length = 64)
    private String partnerId;

    @Column(name = "url", nullable = false, length = 512)
    private String url;

    @Column(name = "modality", nullable = false, length = 32)
    private String modality;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "certificate_alias", length = 128)
    private String certificateAlias;

    @Column(name = "supported_operations", length = 512)
    private String supportedOperations;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
