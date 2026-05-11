package chit.tefca.policy.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "policy_rule_versions", schema = "policy",
        uniqueConstraints = @UniqueConstraint(columnNames = {"rule_id", "version_number"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "versionId")
public class PolicyRuleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version_id")
    private Long versionId;

    @Column(name = "rule_id", nullable = false, length = 64)
    private String ruleId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "rule_expression", nullable = false, columnDefinition = "TEXT")
    private String ruleExpression;

    @Column(name = "changed_by", length = 128)
    private String changedBy;

    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
