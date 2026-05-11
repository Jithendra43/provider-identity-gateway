package chit.tefca.ingress.controller;

import chit.tefca.common.dto.TefcaResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private PolicyServiceClient policyClient;

    @MockBean
    private RoutingServiceClient routingClient;

    @Test
    void healthEndpoint_shouldBeAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void patientDiscovery_withoutAuth_shouldReturn401() throws Exception {
        mockMvc.perform(
                post("/api/v1/tefca/patient-discovery")
                        .contentType("application/json")
                        .content("{\"exchangePurpose\":\"TREATMENT\",\"patientFirstName\":\"John\"}")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void patientDiscovery_withAuth_shouldReturn200() throws Exception {
        when(policyClient.evaluate(anyString(), any(), any(), any(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(new PolicyEvalResult("corr-1", PolicyDecisionType.PERMIT, List.of(), "v1"));
        when(routingClient.route(anyString(), any(), any(), any(), anyString(), any(), any()))
                .thenReturn(new RouteResult("corr-1", "https://target/api", "node-1", 200, Map.of(), 10, 100));

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
         .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void patientDiscovery_policyDenied_shouldReturn200WithDeniedStatus() throws Exception {
        when(policyClient.evaluate(anyString(), any(), any(), any(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(PolicyEvalResult.deny("corr-2", "Denied"));

        mockMvc.perform(
                post("/api/v1/tefca/patient-discovery")
                        .contentType("application/json")
                        .content("{\"exchangePurpose\":\"TREATMENT\",\"patientFirstName\":\"John\"}")
                        .with(jwt().jwt(j -> j
                                .claim("org_id", "org-1")
                                .claim("node_id", "node-1")
                                .audience(List.of("tefca-gateway"))
                        ))
        ).andExpect(status().isOk())
         .andExpect(jsonPath("$.status").value("DENIED"))
         .andExpect(jsonPath("$.errors[0].code").value("POLICY_DENIED"));
    }

    @Test
    void patientDiscovery_invalidBody_shouldReturn400() throws Exception {
        mockMvc.perform(
                post("/api/v1/tefca/patient-discovery")
                        .contentType("application/json")
                        .content("{}")  // missing required fields
                        .with(jwt().jwt(j -> j
                                .claim("org_id", "org-1")
                                .claim("node_id", "node-1")
                                .audience(List.of("tefca-gateway"))
                        ))
        ).andExpect(status().isBadRequest());
    }
}

