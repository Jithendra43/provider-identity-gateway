package chit.tefca.policy.service;

import chit.tefca.common.enums.ExchangePurpose;
import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.PolicyDecisionType;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyEvaluationResponse;
import chit.tefca.policy.engine.PolicyEngine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyEvaluationServiceTest {

    @Mock
    private PolicyEngine policyEngine;

    @Mock
    private PolicyAuditService auditService;

    private PolicyEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new PolicyEvaluationService(
                policyEngine, auditService, new SimpleMeterRegistry());
    }

    @Test
    void shouldEvaluateAndRecordAudit() {
        PolicyEvaluationRequest request = PolicyEvaluationRequest.builder()
                .correlationId("corr-1")
                .operation(TefcaOperation.PATIENT_DISCOVERY)
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .modality(Modality.XCPD)
                .requesterOrgId("ORG-001")
                .requesterNodeId("NODE-001")
                .patientId("P-123")
                .requesterRoles(List.of("CLINICIAN"))
                .build();

        PolicyEvaluationResponse mockResponse = PolicyEvaluationResponse.builder()
                .correlationId("corr-1")
                .decision(PolicyDecisionType.PERMIT)
                .obligations(List.of("AUDIT_TRAIL_REQUIRED"))
                .build();

        when(policyEngine.evaluate(request)).thenReturn(mockResponse);

        PolicyEvaluationResponse result = evaluationService.evaluate(request);

        assertThat(result.getDecision()).isEqualTo(PolicyDecisionType.PERMIT);
        verify(auditService).recordDecision(request, mockResponse);
    }

    @Test
    void shouldRecordAuditOnDeny() {
        PolicyEvaluationRequest request = PolicyEvaluationRequest.builder()
                .correlationId("corr-2")
                .operation(TefcaOperation.DOCUMENT_QUERY)
                .exchangePurpose(ExchangePurpose.OTHER)
                .modality(Modality.XCA_QUERY)
                .requesterOrgId("ORG-001")
                .requesterNodeId("NODE-001")
                .patientId("P-123")
                .requesterRoles(List.of("CLINICIAN"))
                .build();

        PolicyEvaluationResponse mockResponse = PolicyEvaluationResponse.builder()
                .correlationId("corr-2")
                .decision(PolicyDecisionType.DENY)
                .obligations(List.of())
                .build();

        when(policyEngine.evaluate(request)).thenReturn(mockResponse);

        PolicyEvaluationResponse result = evaluationService.evaluate(request);

        assertThat(result.getDecision()).isEqualTo(PolicyDecisionType.DENY);
        verify(auditService).recordDecision(request, mockResponse);
    }
}
