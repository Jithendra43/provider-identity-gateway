package chit.tefca.policy.engine;

import chit.tefca.common.enums.ExchangePurpose;
import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.PolicyDecisionType;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.policy.client.DirectoryCacheClient;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyEvaluationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyEngineTest {

    @Mock
    private DirectoryCacheClient directoryCacheClient;

    private PolicyEngine buildEngine() {
        return new PolicyEngine(
                new ExchangePurposeValidator(),
                new RequesterOrgValidator(directoryCacheClient),
                new ModalityValidator(),
                new PatientRequiredValidator(),
                new RoleAuthorizationValidator(),
                new DelegationRuleValidator(),
                new ConsentValidator(),
                new TimeWindowValidator(),
                new DataClassValidator(),
                new OperationValidator(),
                new ObligationResolver(),
                new SaleOfPhiValidator(),
                new PsychotherapyNotesValidator(),
                new Part2ConsentValidator(),
                new BaaValidator(),
                new BreakglassValidator(),
                new MinimumNecessaryValidator()
        );
    }

    private PolicyEvaluationRequest.PolicyEvaluationRequestBuilder validRequest() {
        return PolicyEvaluationRequest.builder()
                .correlationId("test-123")
                .operation(TefcaOperation.PATIENT_DISCOVERY)
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .modality(Modality.XCPD)
                .requesterOrgId("ORG-001")
                .requesterNodeId("NODE-001")
                .patientId("P-12345")
                .requesterRoles(List.of("CLINICIAN"));
    }

    @Test
    void shouldPermitValidRequest() {
        when(directoryCacheClient.isOrganizationActive("ORG-001")).thenReturn(true);
        PolicyEngine engine = buildEngine();

        PolicyEvaluationResponse response = engine.evaluate(validRequest().build());

        assertThat(response.getDecision()).isEqualTo(PolicyDecisionType.PERMIT);
        assertThat(response.getExplanations()).isNotEmpty();
        assertThat(response.getObligations()).contains("AUDIT_TRAIL_REQUIRED");
    }

    @Test
    void shouldDenyDisallowedPurpose() {
        when(directoryCacheClient.isOrganizationActive("ORG-001")).thenReturn(true);
        PolicyEngine engine = buildEngine();

        PolicyEvaluationResponse response = engine.evaluate(
                validRequest().exchangePurpose(ExchangePurpose.OTHER).build());

        assertThat(response.getDecision()).isEqualTo(PolicyDecisionType.DENY);
        assertThat(response.getObligations()).isEmpty();
    }

    @Test
    void shouldDenyMissingPatientForQueryOp() {
        when(directoryCacheClient.isOrganizationActive("ORG-001")).thenReturn(true);
        PolicyEngine engine = buildEngine();

        // DOCUMENT_QUERY requires patient ID; PATIENT_DISCOVERY is a demographics search
        // and intentionally does not.
        PolicyEvaluationResponse response = engine.evaluate(
                validRequest()
                        .operation(TefcaOperation.DOCUMENT_QUERY)
                        .modality(Modality.XCA_QUERY)
                        .patientId(null)
                        .build());

        assertThat(response.getDecision()).isEqualTo(PolicyDecisionType.DENY);
    }

    @Test
    void shouldPermitMessageDeliveryWithoutPatient() {
        when(directoryCacheClient.isOrganizationActive("ORG-001")).thenReturn(true);
        PolicyEngine engine = buildEngine();

        PolicyEvaluationResponse response = engine.evaluate(
                validRequest()
                        .operation(TefcaOperation.MESSAGE_DELIVERY)
                        .modality(Modality.XDR)
                        .patientId(null)
                        .build());

        assertThat(response.getDecision()).isEqualTo(PolicyDecisionType.PERMIT);
    }

    @Test
    void shouldDenyInactiveOrg() {
        when(directoryCacheClient.isOrganizationActive("ORG-001")).thenReturn(false);
        PolicyEngine engine = buildEngine();

        PolicyEvaluationResponse response = engine.evaluate(validRequest().build());

        assertThat(response.getDecision()).isEqualTo(PolicyDecisionType.DENY);
    }

    @Test
    void shouldDenyMissingRoles() {
        when(directoryCacheClient.isOrganizationActive("ORG-001")).thenReturn(true);
        PolicyEngine engine = buildEngine();

        PolicyEvaluationResponse response = engine.evaluate(
                validRequest().requesterRoles(null).build());

        assertThat(response.getDecision()).isEqualTo(PolicyDecisionType.DENY);
    }

    @Test
    void shouldDenyInvalidModalityForOperation() {
        when(directoryCacheClient.isOrganizationActive("ORG-001")).thenReturn(true);
        PolicyEngine engine = buildEngine();

        // XDR is not allowed for PATIENT_DISCOVERY
        PolicyEvaluationResponse response = engine.evaluate(
                validRequest().modality(Modality.XDR).build());

        assertThat(response.getDecision()).isEqualTo(PolicyDecisionType.DENY);
    }

    @Test
    void shouldIncludeAllSixExplanations() {
        when(directoryCacheClient.isOrganizationActive("ORG-001")).thenReturn(true);
        PolicyEngine engine = buildEngine();

        PolicyEvaluationResponse response = engine.evaluate(validRequest().build());

        assertThat(response.getExplanations()).hasSize(16);
    }

    @Test
    void shouldPermitWhenDirectoryUnavailable() {
        when(directoryCacheClient.isOrganizationActive("ORG-001"))
                .thenThrow(new RuntimeException("Connection refused"));
        PolicyEngine engine = buildEngine();

        // Fail-open: directory unavailability should not block
        PolicyEvaluationResponse response = engine.evaluate(validRequest().build());

        assertThat(response.getDecision()).isEqualTo(PolicyDecisionType.PERMIT);
    }
}
