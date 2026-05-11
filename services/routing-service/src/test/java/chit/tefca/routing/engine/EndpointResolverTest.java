package chit.tefca.routing.engine;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.common.model.Endpoint;
import chit.tefca.routing.client.DirectoryCacheClient;
import chit.tefca.routing.dto.RouteRequest;
import chit.tefca.routing.engine.strategy.RouteStrategySelector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EndpointResolverTest {

    @Mock private DirectoryCacheClient directoryCacheClient;
    @Mock private HealthAwareSelector healthAwareSelector;
    @Mock private RouteStrategySelector strategySelector;

    @InjectMocks private EndpointResolver endpointResolver;

    @Test
    void shouldResolveEndpointByOrgId() {
        RouteRequest request = RouteRequest.builder()
                .correlationId("corr-1")
                .targetOrgId("ORG-001")
                .modality(Modality.XCA_QUERY)
                .operation(TefcaOperation.DOCUMENT_QUERY)
                .build();

        Endpoint ep = Endpoint.builder()
                .endpointId("EP-1").url("https://ep1.test").modality(Modality.XCA_QUERY).active(true).build();

        when(directoryCacheClient.getEndpointsByOrgId("ORG-001")).thenReturn(List.of(ep));
        when(healthAwareSelector.selectHealthy(List.of(ep))).thenReturn(List.of(ep));
        when(strategySelector.resolve(eq(request), any())).thenReturn(List.of(ep));

        Endpoint result = endpointResolver.resolve(request);

        assertThat(result.getEndpointId()).isEqualTo("EP-1");
        assertThat(result.getUrl()).isEqualTo("https://ep1.test");
    }

    @Test
    void shouldResolveEndpointByNodeId() {
        RouteRequest request = RouteRequest.builder()
                .correlationId("corr-2")
                .targetOrgId("ORG-001")
                .targetNodeId("NODE-001")
                .modality(Modality.FHIR)
                .operation(TefcaOperation.FHIR_PROXY)
                .build();

        Endpoint ep = Endpoint.builder()
                .endpointId("EP-2").nodeId("NODE-001").url("https://fhir.test/r4")
                .modality(Modality.FHIR).active(true).build();

        when(directoryCacheClient.getEndpointsByNodeId("NODE-001")).thenReturn(List.of(ep));
        when(healthAwareSelector.selectHealthy(List.of(ep))).thenReturn(List.of(ep));
        when(strategySelector.resolve(eq(request), any())).thenReturn(List.of(ep));

        Endpoint result = endpointResolver.resolve(request);

        assertThat(result.getNodeId()).isEqualTo("NODE-001");
    }

    @Test
    void shouldThrowWhenNoEndpointFound() {
        RouteRequest request = RouteRequest.builder()
                .correlationId("corr-3")
                .targetOrgId("ORG-MISSING")
                .modality(Modality.XCPD)
                .operation(TefcaOperation.PATIENT_DISCOVERY)
                .build();

        when(directoryCacheClient.getEndpointsByOrgId("ORG-MISSING")).thenReturn(List.of());

        assertThatThrownBy(() -> endpointResolver.resolve(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No endpoint found");
    }

    @Test
    void shouldThrowWhenStrategyReturnsEmpty() {
        RouteRequest request = RouteRequest.builder()
                .correlationId("corr-4")
                .targetOrgId("ORG-001")
                .modality(Modality.XDR)
                .operation(TefcaOperation.MESSAGE_DELIVERY)
                .build();

        Endpoint ep = Endpoint.builder()
                .endpointId("EP-3").modality(Modality.XDR).active(true).build();

        when(directoryCacheClient.getEndpointsByOrgId("ORG-001")).thenReturn(List.of(ep));
        when(healthAwareSelector.selectHealthy(List.of(ep))).thenReturn(List.of(ep));
        when(strategySelector.resolve(eq(request), any())).thenReturn(List.of());

        assertThatThrownBy(() -> endpointResolver.resolve(request))
                .isInstanceOf(IllegalStateException.class);
    }
}
