package chit.tefca.directory.model;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "directory_capabilities", schema = "directory",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cap_node_modality_op",
                columnNames = {"node_id", "modality", "operation"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "capabilityId")
@ToString(exclude = "node")
public class DirectoryCapability {

    @Id
    @Column(name = "capability_id", length = 64)
    private String capabilityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false, insertable = false, updatable = false)
    private DirectoryNode node;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Modality modality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private TefcaOperation operation;

    @Column(nullable = false)
    private boolean enabled;

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
