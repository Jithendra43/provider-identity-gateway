package chit.tefca.directory.model;

import chit.tefca.common.enums.NodeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "directory_nodes", schema = "directory")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "nodeId")
@ToString(exclude = {"endpoints", "capabilities", "organization"})
public class DirectoryNode {

    @Id
    @Column(name = "node_id", length = 64)
    private String nodeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false, insertable = false, updatable = false)
    private DirectoryOrganization organization;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(nullable = false)
    private String name;

    @Column(name = "home_community_id")
    private String homeCommunityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeStatus status;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "node", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DirectoryEndpoint> endpoints = new ArrayList<>();

    @OneToMany(mappedBy = "node", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DirectoryCapability> capabilities = new ArrayList<>();

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
