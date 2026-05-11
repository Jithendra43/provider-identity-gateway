package chit.tefca.routing.engine.strategy;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.common.model.Endpoint;
import chit.tefca.routing.dto.RouteRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FanOutRouteStrategyTest {

    private final FanOutRouteStrategy strategy = new FanOutRouteStrategy();

    @Test
    void shouldReturnAllMatchingEndpoints() {
        Endpoint ep1 = Endpoint.builder().endpointId("EP-1").modality(Modality.XCPD).active(true).build();
        Endpoint ep2 = Endpoint.builder().endpointId("EP-2").modality(Modality.XCPD).active(true).build();
        Endpoint ep3 = Endpoint.builder().endpointId("EP-3").modality(Modality.FHIR).active(true).build();

        RouteRequest request = RouteRequest.builder()
                .operation(TefcaOperation.PATIENT_DISCOVERY).modality(Modality.XCPD).build();

        List<Endpoint> result = strategy.resolve(request, List.of(ep1, ep2, ep3));

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldSupportPatientDiscovery() {
        RouteRequest pdRequest = RouteRequest.builder().operation(TefcaOperation.PATIENT_DISCOVERY).build();
        assertThat(strategy.supports(pdRequest)).isTrue();
    }

    @Test
    void shouldNotSupportDocumentQuery() {
        RouteRequest dqRequest = RouteRequest.builder().operation(TefcaOperation.DOCUMENT_QUERY).build();
        assertThat(strategy.supports(dqRequest)).isFalse();
    }
}
