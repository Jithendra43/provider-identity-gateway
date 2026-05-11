package chit.tefca.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.common.model.Endpoint;
import chit.tefca.routing.client.DirectoryCacheClient;
import chit.tefca.routing.dto.RouteRequest;
import chit.tefca.routing.dto.RouteResponse;
import chit.tefca.routing.engine.IdempotencyManager;
import chit.tefca.routing.engine.TransactionForwarder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoutingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private IdempotencyManager idempotencyManager;

    @MockBean private DirectoryCacheClient directoryCacheClient;
    @MockBean private TransactionForwarder transactionForwarder;

    @Test
    void shouldRouteTransactionSuccessfully() throws Exception {
        Endpoint ep = Endpoint.builder()
                .endpointId("EP-1").nodeId("NODE-1")
                .url("https://target.test/api")
                .modality(Modality.XCA_QUERY).active(true).build();

        when(directoryCacheClient.getEndpointsByOrgId("ORG-TARGET"))
                .thenReturn(List.of(ep));

        RouteResponse fwdResponse = RouteResponse.builder()
                .correlationId("int-1")
                .resolvedEndpointUrl("https://target.test/api")
                .resolvedNodeId("NODE-1")
                .httpStatus(200)
                .responsePayload(Map.of("status", "OK"))
                .build();
        when(transactionForwarder.forward(any(), any())).thenReturn(fwdResponse);

        RouteRequest request = RouteRequest.builder()
                .correlationId("int-1")
                .operation(TefcaOperation.DOCUMENT_QUERY)
                .modality(Modality.XCA_QUERY)
                .targetOrgId("ORG-TARGET")
                .requesterOrgId("ORG-REQ")
                .payload(Map.of("query", "test"))
                .build();

        mockMvc.perform(post("/api/v1/routing/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correlationId").value("int-1"))
                .andExpect(jsonPath("$.resolvedEndpointUrl").value("https://target.test/api"));
    }

    @Test
    void shouldReturn409ForDuplicateRequest() throws Exception {
        idempotencyManager.markProcessed("dup-key", "prev-corr");

        RouteRequest request = RouteRequest.builder()
                .correlationId("int-2")
                .operation(TefcaOperation.DOCUMENT_QUERY)
                .modality(Modality.XCA_QUERY)
                .targetOrgId("ORG-TARGET")
                .requesterOrgId("ORG-REQ")
                .idempotencyKey("dup-key")
                .build();

        mockMvc.perform(post("/api/v1/routing/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus", is(409)))
                .andExpect(jsonPath("$.responsePayload.error").value("DUPLICATE_REQUEST"));
    }

    @Test
    void shouldReturnBadRequestForMissingFields() throws Exception {
        String incomplete = """
                { "correlationId": "int-3" }
                """;

        mockMvc.perform(post("/api/v1/routing/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incomplete))
                .andExpect(status().isBadRequest());
    }
}
