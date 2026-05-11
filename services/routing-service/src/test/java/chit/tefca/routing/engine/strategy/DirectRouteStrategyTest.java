package chit.tefca.routing.engine.strategy;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.common.model.Endpoint;
import chit.tefca.routing.dto.RouteRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DirectRouteStrategyTest {

    private final DirectRouteStrategy strategy = new DirectRouteStrategy();

    @Test
    void shouldSelectFirstMatchingEndpoint() {
        Endpoint ep1 = Endpoint.builder().endpointId("EP-1").modality(Modality.XCA_QUERY).active(true).build();
        Endpoint ep2 = Endpoint.builder().endpointId("EP-2").modality(Modality.XCA_QUERY).active(true).build();
        Endpoint ep3 = Endpoint.builder().endpointId("EP-3").modality(Modality.FHIR).active(true).build();

        RouteRequest request = RouteRequest.builder()
                .operation(TefcaOperation.DOCUMENT_QUERY).modality(Modality.XCA_QUERY).build();

        List<Endpoint> result = strategy.resolve(request, List.of(ep1, ep2, ep3));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEndpointId()).isEqualTo("EP-1");
    }

    @Test
    void shouldReturnEmptyWhenNoModalityMatch() {
        Endpoint ep = Endpoint.builder().endpointId("EP-1").modality(Modality.FHIR).active(true).build();

        RouteRequest request = RouteRequest.builder()
                .operation(TefcaOperation.DOCUMENT_QUERY).modality(Modality.XCA_QUERY).build();

        List<Endpoint> result = strategy.resolve(request, List.of(ep));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldSkipInactiveEndpoints() {
        Endpoint inactive = Endpoint.builder().endpointId("EP-1").modality(Modality.FHIR).active(false).build();
        Endpoint active = Endpoint.builder().endpointId("EP-2").modality(Modality.FHIR).active(true).build();

        RouteRequest request = RouteRequest.builder()
                .operation(TefcaOperation.FHIR_PROXY).modality(Modality.FHIR).build();

        List<Endpoint> result = strategy.resolve(request, List.of(inactive, active));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEndpointId()).isEqualTo("EP-2");
    }

    @Test
    void shouldSupportAllRequests() {
        RouteRequest request = RouteRequest.builder().operation(TefcaOperation.DOCUMENT_QUERY).build();
        assertThat(strategy.supports(request)).isTrue();
    }
}
