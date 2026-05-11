package chit.tefca.ingress.service;

import chit.tefca.common.audit.AuditPublisher;
import chit.tefca.common.dto.TefcaResponse;
import chit.tefca.common.enums.ExchangePurpose;
import chit.tefca.common.enums.PolicyDecisionType;
import chit.tefca.common.model.RequesterIdentity;
import chit.tefca.ingress.client.PolicyServiceClient;
import chit.tefca.ingress.client.PolicyServiceClient.PolicyEvalResult;
import chit.tefca.ingress.client.RoutingServiceClient;
import chit.tefca.ingress.client.RoutingServiceClient.RouteResult;
import chit.tefca.ingress.config.AdminProperties;
import chit.tefca.ingress.dto.PatientDiscoveryRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngressOrchestratorTest {

    @Mock
    private PolicyServiceClient policyClient;

    @Mock
    private RoutingServiceClient routingClient;

    @Mock
    private RequestNormalizer requestNormalizer;

    @Mock
    private AccessLogService accessLogService;

    @Mock
    private AuditPublisher auditPublisher;

    private IngressOrchestrator orchestrator;

    private RequesterIdentity identity;

    @BeforeEach
    void setUp() {
        orchestrator = new IngressOrchestrator(
                policyClient, routingClient, requestNormalizer, accessLogService,
                auditPublisher, new AdminProperties(), new SimpleMeterRegistry()
        );
        identity = RequesterIdentity.builder()
                .orgId("org-1")
                .nodeId("node-1")
                .roles(List.of("CLINICIAN"))
                .build();
    }

    @Test
    void successfulFlow_shouldReturnSuccess() {
        PatientDiscoveryRequest request = PatientDiscoveryRequest.builder()
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .patientFirstName("John")
                .targetOrgId("target-org")
                .build();

        // Normalizer returns a TefcaRequest
        var tefcaRequest = chit.tefca.common.dto.TefcaRequest.builder()
                .correlationId("corr-1")
                .operation(chit.tefca.common.enums.TefcaOperation.PATIENT_DISCOVERY)
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .requesterOrgId("org-1")
                .requesterNodeId("node-1")
                .targetOrgId("target-org")
                .build();
        when(requestNormalizer.normalizePatientDiscovery(request, "org-1", "node-1")).thenReturn(tefcaRequest);

        // Policy permits
        when(policyClient.evaluate(anyString(), any(), any(), any(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(new PolicyEvalResult("corr-1", PolicyDecisionType.PERMIT, List.of("AUDIT_TRAIL_REQUIRED"), "v1"));

        // Routing succeeds
        when(routingClient.route(anyString(), any(), any(), any(), anyString(), any(), any()))
                .thenReturn(new RouteResult("corr-1", "https://target/api", "target-node", 200,
                        Map.of("patientId", "P001"), 50, 200));

        TefcaResponse response = orchestrator.processPatientDiscovery(request, identity);

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getCorrelationId()).isEqualTo("corr-1");
        assertThat(response.getObligations()).contains("AUDIT_TRAIL_REQUIRED");
    }

    @Test
    void policyDenied_shouldReturnDenied() {
        PatientDiscoveryRequest request = PatientDiscoveryRequest.builder()
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .patientFirstName("John")
                .build();

        var tefcaRequest = chit.tefca.common.dto.TefcaRequest.builder()
                .correlationId("corr-2")
                .operation(chit.tefca.common.enums.TefcaOperation.PATIENT_DISCOVERY)
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .requesterOrgId("org-1")
                .requesterNodeId("node-1")
                .build();
        when(requestNormalizer.normalizePatientDiscovery(request, "org-1", "node-1")).thenReturn(tefcaRequest);

        when(policyClient.evaluate(anyString(), any(), any(), any(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(PolicyEvalResult.deny("corr-2", "Denied by policy"));

        TefcaResponse response = orchestrator.processPatientDiscovery(request, identity);

        assertThat(response.getStatus()).isEqualTo("DENIED");
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().get(0).getCode()).isEqualTo("POLICY_DENIED");
    }

    @Test
    void routingFailure_shouldReturnError() {
        PatientDiscoveryRequest request = PatientDiscoveryRequest.builder()
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .patientFirstName("John")
                .targetOrgId("target-org")
                .build();

        var tefcaRequest = chit.tefca.common.dto.TefcaRequest.builder()
                .correlationId("corr-3")
                .operation(chit.tefca.common.enums.TefcaOperation.PATIENT_DISCOVERY)
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .requesterOrgId("org-1")
                .requesterNodeId("node-1")
                .targetOrgId("target-org")
                .build();
        when(requestNormalizer.normalizePatientDiscovery(request, "org-1", "node-1")).thenReturn(tefcaRequest);

        when(policyClient.evaluate(anyString(), any(), any(), any(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(new PolicyEvalResult("corr-3", PolicyDecisionType.PERMIT, List.of(), "v1"));

        when(routingClient.route(anyString(), any(), any(), any(), anyString(), any(), any()))
                .thenReturn(RouteResult.failure("corr-3", 503, "Service unavailable"));

        TefcaResponse response = orchestrator.processPatientDiscovery(request, identity);

        assertThat(response.getStatus()).isEqualTo("ERROR");
        assertThat(response.getErrors()).hasSize(1);
        assertThat(response.getErrors().get(0).getCode()).isEqualTo("ROUTING_FAILURE");
    }

    @Test
    void noIdentity_shouldUseUnknown() {
        PatientDiscoveryRequest request = PatientDiscoveryRequest.builder()
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .patientFirstName("Jane")
                .build();

        var tefcaRequest = chit.tefca.common.dto.TefcaRequest.builder()
                .correlationId("corr-4")
                .operation(chit.tefca.common.enums.TefcaOperation.PATIENT_DISCOVERY)
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .requesterOrgId("ORG-ADMIN-CONSOLE")
                .requesterNodeId("NODE-ADMIN-CONSOLE")
                .build();
        // When no identity is supplied (e.g. an admin-console request whose
        // Cognito ID token carries no orgId/nodeId claims) the orchestrator
        // falls back to the configured tefca.admin.test-harness identity
        // rather than synthesising a literal "unknown" — the policy engine
        // can then resolve a real, directory-bound requester.
        when(requestNormalizer.normalizePatientDiscovery(request, "ORG-ADMIN-CONSOLE", "NODE-ADMIN-CONSOLE"))
                .thenReturn(tefcaRequest);

        when(policyClient.evaluate(anyString(), any(), any(), any(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(new PolicyEvalResult("corr-4", PolicyDecisionType.PERMIT, List.of(), "v1"));

        when(routingClient.route(anyString(), any(), any(), any(), anyString(), any(), any()))
                .thenReturn(new RouteResult("corr-4", "https://target/api", "node-x", 200, Map.of(), 10, 100));

        TefcaResponse response = orchestrator.processPatientDiscovery(request, null);

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
    }
}
