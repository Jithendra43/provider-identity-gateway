package chit.tefca.directory.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "directory_organizations", schema = "directory")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "orgId")
@ToString(exclude = "nodes")
public class DirectoryOrganization {

    @Id
    @Column(name = "org_id", length = 64)
    private String orgId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String oid;

    @Column(name = "org_type", length = 32)
    private String orgType;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "home_community_id")
    private String homeCommunityId;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DirectoryNode> nodes = new ArrayList<>();

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
