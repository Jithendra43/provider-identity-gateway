package chit.tefca.ingress.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "partner_oauth_config", schema = "ingress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerOauthConfig {

    @Id
    @Column(name = "config_id", length = 64)
    private String configId;

    @Column(name = "partner_id", nullable = false, unique = true, length = 64)
    private String partnerId;

    @Column(name = "client_id", nullable = false, length = 256)
    private String clientId;

    @Column(name = "allowed_scopes", columnDefinition = "text[]")
    private String[] allowedScopes;

    @Column(name = "token_ttl_sec", nullable = false)
    private int tokenTtlSec;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
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
