package chit.tefca.policy.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "policy_audit_entries", schema = "policy")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "auditId")
public class PolicyAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "requester_org_id", nullable = false, length = 64)
    private String requesterOrgId;

    @Column(name = "target_org_id", length = 64)
    private String targetOrgId;

    @Column(nullable = false, length = 32)
    private String operation;

    @Column(name = "exchange_purpose", nullable = false, length = 64)
    private String exchangePurpose;

    @Column(nullable = false, length = 16)
    private String decision;

    @Column(name = "policy_version", length = 32)
    private String policyVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation_json", columnDefinition = "TEXT")
    private String explanationJson;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @PrePersist
    protected void onPersist() {
        if (evaluatedAt == null) {
            evaluatedAt = Instant.now();
        }
    }
}
