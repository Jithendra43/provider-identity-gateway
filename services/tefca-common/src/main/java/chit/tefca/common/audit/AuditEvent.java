package chit.tefca.common.audit;

import chit.tefca.common.enums.TefcaOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical audit event for TEFCA gateway operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    private String eventId;
    private String correlationId;
    private String eventType;
    private TefcaOperation operation;
    private String requesterOrgId;
    private String requesterNodeId;
    private String targetOrgId;
    private String targetNodeId;
    private String outcome;
    private String outcomeDetail;
    private String policyDecision;
    private Map<String, String> metadata;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
