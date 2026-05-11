package chit.tefca.common.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * JPA entity mapping to audit.audit_events table (V005 migration).
 */
@Entity
@Table(name = "audit_events", schema = "audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventEntity {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "operation", length = 32)
    private String operation;

    @Column(name = "requester_org_id", length = 64)
    private String requesterOrgId;

    @Column(name = "target_org_id", length = 64)
    private String targetOrgId;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, String> metadata;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    public static AuditEventEntity fromAuditEvent(AuditEvent event) {
        return AuditEventEntity.builder()
                .eventId(event.getEventId())
                .correlationId(event.getCorrelationId())
                .eventType(event.getEventType())
                .operation(event.getOperation() != null ? event.getOperation().name() : null)
                .requesterOrgId(event.getRequesterOrgId())
                .targetOrgId(event.getTargetOrgId())
                .outcome(event.getOutcome())
                .metadata(event.getMetadata())
                .createdAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .build();
    }
}
