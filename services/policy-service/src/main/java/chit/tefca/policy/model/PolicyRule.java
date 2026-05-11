package chit.tefca.policy.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "policy_rules", schema = "policy")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "ruleId")
@ToString(exclude = "ruleExpression")
public class PolicyRule {

    @Id
    @Column(name = "rule_id", length = 64)
    private String ruleId;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "rule_expression", nullable = false, columnDefinition = "TEXT")
    private String ruleExpression;

    @Column(nullable = false)
    @Builder.Default
    private int priority = 100;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

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
