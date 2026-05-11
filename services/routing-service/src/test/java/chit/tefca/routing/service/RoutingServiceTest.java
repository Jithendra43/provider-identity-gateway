package chit.tefca.routing.service;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.common.model.Endpoint;
import chit.tefca.routing.dto.RouteRequest;
import chit.tefca.routing.dto.RouteResponse;
import chit.tefca.routing.engine.EndpointResolver;
import chit.tefca.routing.engine.IdempotencyManager;
import chit.tefca.routing.engine.TransactionForwarder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    @Mock private EndpointResolver endpointResolver;
    @Mock private TransactionForwarder transactionForwarder;
    @Mock private IdempotencyManager idempotencyManager;

    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        routingService = new RoutingService(
                endpointResolver, transactionForwarder, idempotencyManager,
                new SimpleMeterRegistry());
    }

    @Test
    void shouldRouteSuccessfully() {
        RouteRequest request = RouteRequest.builder()
                .correlationId("corr-1")
                .operation(TefcaOperation.DOCUMENT_QUERY)
                .modality(Modality.XCA_QUERY)
                .targetOrgId("ORG-001")
                .requesterOrgId("ORG-REQ")
                .build();

        Endpoint ep = Endpoint.builder()
                .endpointId("EP-1").nodeId("NODE-1")
                .url("https://ep.test").modality(Modality.XCA_QUERY).active(true).build();

        RouteResponse fwdResponse = RouteResponse.builder()
                .correlationId("corr-1").httpStatus(200)
                .resolvedEndpointUrl("https://ep.test")
                .resolvedNodeId("NODE-1")
                .responsePayload(Map.of("status", "OK"))
                .completedAt(Instant.now()).build();

        when(idempotencyManager.isDuplicate(any())).thenReturn(false);
        when(endpointResolver.resolve(request)).thenReturn(ep);
        when(transactionForwarder.forward(request, ep)).thenReturn(fwdResponse);

        RouteResponse result = routingService.routeTransaction(request);

        assertThat(result.getHttpStatus()).isEqualTo(200);
        assertThat(result.getResolvedEndpointUrl()).isEqualTo("https://ep.test");
    }

    @Test
    void shouldReturnDuplicateResponse() {
        RouteRequest request = RouteRequest.builder()
                .correlationId("corr-2")
                .operation(TefcaOperation.DOCUMENT_QUERY)
                .modality(Modality.XCA_QUERY)
                .targetOrgId("ORG-001")
                .requesterOrgId("ORG-REQ")
                .idempotencyKey("dup-key")
                .build();

        when(idempotencyManager.isDuplicate("dup-key")).thenReturn(true);

        RouteResponse result = routingService.routeTransaction(request);

        assertThat(result.getHttpStatus()).isEqualTo(409);
        verify(endpointResolver, never()).resolve(any());
        verify(transactionForwarder, never()).forward(any(), any());
    }

    @Test
    void shouldRecordMetricsForFailure() {
        RouteRequest request = RouteRequest.builder()
                .correlationId("corr-3")
                .operation(TefcaOperation.DOCUMENT_QUERY)
                .modality(Modality.XCA_QUERY)
                .targetOrgId("ORG-001")
                .requesterOrgId("ORG-REQ")
                .build();

        Endpoint ep = Endpoint.builder()
                .endpointId("EP-1").url("https://ep.test").active(true).build();

        RouteResponse fwdResponse = RouteResponse.builder()
                .correlationId("corr-3").httpStatus(500)
                .resolvedEndpointUrl("https://ep.test")
                .responsePayload(Map.of("error", "INTERNAL"))
                .completedAt(Instant.now()).build();

        when(idempotencyManager.isDuplicate(any())).thenReturn(false);
        when(endpointResolver.resolve(request)).thenReturn(ep);
        when(transactionForwarder.forward(request, ep)).thenReturn(fwdResponse);

        RouteResponse result = routingService.routeTransaction(request);

        assertThat(result.getHttpStatus()).isEqualTo(500);
    }
}
