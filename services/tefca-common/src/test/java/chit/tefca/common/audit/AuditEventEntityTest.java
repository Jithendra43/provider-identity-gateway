package chit.tefca.common.audit;

import chit.tefca.common.enums.TefcaOperation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventEntityTest {

    @Test
    void fromAuditEvent_shouldMapAllFields() {
        AuditEvent event = AuditEvent.builder()
                .eventId("evt-1")
                .correlationId("corr-1")
                .eventType("INGRESS_PATIENT_DISCOVERY")
                .operation(TefcaOperation.PATIENT_DISCOVERY)
                .requesterOrgId("org-1")
                .requesterNodeId("node-1")
                .targetOrgId("org-2")
                .targetNodeId("node-2")
                .outcome("SUCCESS")
                .policyDecision("PERMIT")
                .metadata(Map.of("key", "value"))
                .timestamp(Instant.parse("2024-01-01T00:00:00Z"))
                .build();

        AuditEventEntity entity = AuditEventEntity.fromAuditEvent(event);

        assertThat(entity.getEventId()).isEqualTo("evt-1");
        assertThat(entity.getCorrelationId()).isEqualTo("corr-1");
        assertThat(entity.getEventType()).isEqualTo("INGRESS_PATIENT_DISCOVERY");
        assertThat(entity.getOperation()).isEqualTo("PATIENT_DISCOVERY");
        assertThat(entity.getRequesterOrgId()).isEqualTo("org-1");
        assertThat(entity.getTargetOrgId()).isEqualTo("org-2");
        assertThat(entity.getOutcome()).isEqualTo("SUCCESS");
        assertThat(entity.getMetadata()).containsEntry("key", "value");
        assertThat(entity.getCreatedAt()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    void fromAuditEvent_shouldHandleNullOperation() {
        AuditEvent event = AuditEvent.builder()
                .eventId("evt-2")
                .correlationId("corr-2")
                .eventType("SYSTEM_EVENT")
                .outcome("SUCCESS")
                .build();

        AuditEventEntity entity = AuditEventEntity.fromAuditEvent(event);

        assertThat(entity.getOperation()).isNull();
        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    void fromAuditEvent_shouldHandleNullTimestamp() {
        AuditEvent event = AuditEvent.builder()
                .eventId("evt-3")
                .correlationId("corr-3")
                .eventType("TEST")
                .outcome("SUCCESS")
                .timestamp(null)
                .build();

        AuditEventEntity entity = AuditEventEntity.fromAuditEvent(event);

        assertThat(entity.getCreatedAt()).isNotNull();
    }
}
