package chit.tefca.ingress.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "partners", schema = "ingress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Partner {

    @Id
    @Column(name = "partner_id", length = 64)
    private String partnerId;

    @Column(name = "org_id", nullable = false, unique = true, length = 64)
    private String orgId;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "environment", nullable = false, length = 32)
    private String environment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

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
