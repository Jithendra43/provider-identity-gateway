package chit.tefca.policy;

import chit.tefca.common.enums.ExchangePurpose;
import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.policy.client.DirectoryCacheClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PolicyEvaluationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DirectoryCacheClient directoryCacheClient;

    @BeforeEach
    void setUp() {
        lenient().when(directoryCacheClient.isOrganizationActive(anyString())).thenReturn(true);
    }

    @Test
    void shouldPermitValidTreatmentRequest() throws Exception {
        PolicyEvaluationRequest request = PolicyEvaluationRequest.builder()
                .correlationId("int-corr-1")
                .operation(TefcaOperation.PATIENT_DISCOVERY)
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .modality(Modality.XCPD)
                .requesterOrgId("ORG-001")
                .requesterNodeId("NODE-001")
                .patientId("P-123")
                .requesterRoles(List.of("CLINICIAN"))
                .build();

        mockMvc.perform(post("/api/v1/policy/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correlationId").value("int-corr-1"))
                .andExpect(jsonPath("$.decision").value("PERMIT"))
                .andExpect(jsonPath("$.obligations", hasItem("AUDIT_TRAIL_REQUIRED")));
    }

    @Test
    void shouldDenyRequestWithDisallowedPurpose() throws Exception {
        PolicyEvaluationRequest request = PolicyEvaluationRequest.builder()
                .correlationId("int-corr-2")
                .operation(TefcaOperation.PATIENT_DISCOVERY)
                .exchangePurpose(ExchangePurpose.OTHER)
                .modality(Modality.XCPD)
                .requesterOrgId("ORG-001")
                .requesterNodeId("NODE-001")
                .patientId("P-123")
                .requesterRoles(List.of("CLINICIAN"))
                .build();

        mockMvc.perform(post("/api/v1/policy/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.explanations", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void shouldDenyRequestWithMissingPatient() throws Exception {
        PolicyEvaluationRequest request = PolicyEvaluationRequest.builder()
                .correlationId("int-corr-3")
                .operation(TefcaOperation.DOCUMENT_QUERY)
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .modality(Modality.XCA_QUERY)
                .requesterOrgId("ORG-001")
                .requesterNodeId("NODE-001")
                .requesterRoles(List.of("CLINICIAN"))
                .build();

        mockMvc.perform(post("/api/v1/policy/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"));
    }

    @Test
    void shouldReturnBadRequestForMissingRequiredFields() throws Exception {
        String incompleteJson = """
                {
                    "correlationId": "int-corr-4"
                }
                """;

        mockMvc.perform(post("/api/v1/policy/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incompleteJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDenyWhenNoRolesProvided() throws Exception {
        PolicyEvaluationRequest request = PolicyEvaluationRequest.builder()
                .correlationId("int-corr-5")
                .operation(TefcaOperation.PATIENT_DISCOVERY)
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .modality(Modality.XCPD)
                .requesterOrgId("ORG-001")
                .requesterNodeId("NODE-001")
                .patientId("P-123")
                .build();

        mockMvc.perform(post("/api/v1/policy/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"));
    }
}
