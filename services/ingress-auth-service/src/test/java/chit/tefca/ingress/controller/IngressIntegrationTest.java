package chit.tefca.ingress.controller;

import chit.tefca.common.enums.PolicyDecisionType;
import chit.tefca.ingress.client.PolicyServiceClient;
import chit.tefca.ingress.client.PolicyServiceClient.PolicyEvalResult;
import chit.tefca.ingress.client.RoutingServiceClient;
import chit.tefca.ingress.client.RoutingServiceClient.RouteResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IngressIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private PolicyServiceClient policyClient;

    @MockBean
    private RoutingServiceClient routingClient;

    @Test
    void documentQuery_withAuth_shouldSucceed() throws Exception {
        when(policyClient.evaluate(anyString(), any(), any(), any(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(new PolicyEvalResult("corr-1", PolicyDecisionType.PERMIT, List.of(), "v1"));
        when(routingClient.route(anyString(), any(), any(), any(), anyString(), any(), any()))
                .thenReturn(new RouteResult("corr-1", "https://target/api", "node-1", 200, Map.of(), 10, 100));

        mockMvc.perform(
                post("/api/v1/tefca/document-query")
                        .contentType("application/json")
                        .content("{\"exchangePurpose\":\"TREATMENT\",\"patientId\":\"P001\",\"patientIdSystem\":\"urn:oid:1.2.3\",\"targetOrgId\":\"org-2\"}")
                        .with(jwt().jwt(j -> j
                                .claim("org_id", "org-1")
                                .claim("node_id", "node-1")
                                .audience(List.of("tefca-gateway"))
                        ))
        ).andExpect(status().isOk())
         .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void documentRetrieve_withAuth_shouldSucceed() throws Exception {
        when(policyClient.evaluate(anyString(), any(), any(), any(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(new PolicyEvalResult("corr-2", PolicyDecisionType.PERMIT, List.of("AUDIT_TRAIL_REQUIRED"), "v1"));
        when(routingClient.route(anyString(), any(), any(), any(), anyString(), any(), any()))
                .thenReturn(new RouteResult("corr-2", "https://target/api", "node-1", 200, Map.of("content", "base64data"), 10, 100));

        mockMvc.perform(
                post("/api/v1/tefca/document-retrieve")
                        .contentType("application/json")
                        .content("{\"exchangePurpose\":\"TREATMENT\",\"documentId\":\"DOC001\",\"repositoryId\":\"REPO1\",\"patientId\":\"P001\"}")
                        .with(jwt().jwt(j -> j
                                .claim("org_id", "org-1")
                                .claim("node_id", "node-1")
                                .audience(List.of("tefca-gateway"))
                        ))
        ).andExpect(status().isOk())
         .andExpect(jsonPath("$.status").value("SUCCESS"))
         .andExpect(jsonPath("$.obligations[0]").value("AUDIT_TRAIL_REQUIRED"));
    }

    @Test
    void messageDelivery_withAuth_shouldSucceed() throws Exception {
        when(policyClient.evaluate(anyString(), any(), any(), any(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(new PolicyEvalResult("corr-3", PolicyDecisionType.PERMIT, List.of(), "v1"));
        when(routingClient.route(anyString(), any(), any(), any(), anyString(), any(), any()))
                .thenReturn(new RouteResult("corr-3", "https://target/api", "node-1", 200, Map.of("messageId", "MSG001"), 10, 100));

        mockMvc.perform(
                post("/api/v1/tefca/message-delivery")
                        .contentType("application/json")
                        .content("{\"exchangePurpose\":\"TREATMENT\",\"targetOrgId\":\"org-2\",\"messageType\":\"ADT\",\"messageBody\":{\"content\":\"test\"}}")
                        .with(jwt().jwt(j -> j
                                .claim("org_id", "org-1")
                                .claim("node_id", "node-1")
                                .audience(List.of("tefca-gateway"))
                        ))
        ).andExpect(status().isOk())
         .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void routingFailure_shouldReturnErrorStatus() throws Exception {
        when(policyClient.evaluate(anyString(), any(), any(), any(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(new PolicyEvalResult("corr-4", PolicyDecisionType.PERMIT, List.of(), "v1"));
        when(routingClient.route(anyString(), any(), any(), any(), anyString(), any(), any()))
                .thenReturn(RouteResult.failure("corr-4", 503, "Service unavailable"));

        mockMvc.perform(
                post("/api/v1/tefca/patient-discovery")
                        .contentType("application/json")
                        .content("{\"exchangePurpose\":\"TREATMENT\",\"patientFirstName\":\"John\",\"targetOrgId\":\"org-2\"}")
                        .with(jwt().jwt(j -> j
                                .claim("org_id", "org-1")
                                .claim("node_id", "node-1")
                                .audience(List.of("tefca-gateway"))
                        ))
        ).andExpect(status().isOk())
         .andExpect(jsonPath("$.status").value("ERROR"))
         .andExpect(jsonPath("$.errors[0].code").value("ROUTING_FAILURE"));
    }

    @Test
    void fhirProxy_withAuth_shouldReturnStub() throws Exception {
        mockMvc.perform(
                post("/api/v1/tefca/fhir-proxy/Patient")
                        .contentType("application/json")
                        .content("{\"resourceType\":\"Patient\"}")
                        .with(jwt().jwt(j -> j
                                .claim("org_id", "org-1")
                                .claim("node_id", "node-1")
                                .audience(List.of("tefca-gateway"))
                        ))
        ).andExpect(status().isOk())
         .andExpect(jsonPath("$.status").value("fhir-proxy-stub"));
    }

    @Test
    void securityHeaders_shouldBePresent() throws Exception {
        mockMvc.perform(
                post("/api/v1/tefca/patient-discovery")
                        .contentType("application/json")
                        .content("{\"exchangePurpose\":\"TREATMENT\",\"patientFirstName\":\"John\"}")
        ).andExpect(header().string("X-Content-Type-Options", "nosniff"))
         .andExpect(header().string("X-Frame-Options", "DENY"))
         .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
    }
}
