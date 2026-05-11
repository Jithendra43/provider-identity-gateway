package chit.tefca.routing.engine;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.model.Endpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultHealthAwareSelectorTest {

    @Mock private EndpointHealthTracker healthTracker;
    @InjectMocks private DefaultHealthAwareSelector selector;

    @Test
    void shouldReturnOnlyHealthyEndpoints() {
        Endpoint healthy = Endpoint.builder().endpointId("EP-1").modality(Modality.FHIR).active(true).build();
        Endpoint unhealthy = Endpoint.builder().endpointId("EP-2").modality(Modality.FHIR).active(true).build();

        when(healthTracker.isHealthy("EP-1")).thenReturn(true);
        when(healthTracker.isHealthy("EP-2")).thenReturn(false);

        List<Endpoint> result = selector.selectHealthy(List.of(healthy, unhealthy));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEndpointId()).isEqualTo("EP-1");
    }

    @Test
    void shouldReturnAllEndpointsWhenAllUnhealthy() {
        Endpoint ep1 = Endpoint.builder().endpointId("EP-1").active(true).build();
        Endpoint ep2 = Endpoint.builder().endpointId("EP-2").active(true).build();

        when(healthTracker.isHealthy("EP-1")).thenReturn(false);
        when(healthTracker.isHealthy("EP-2")).thenReturn(false);

        List<Endpoint> result = selector.selectHealthy(List.of(ep1, ep2));

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldReturnEmptyForEmptyInput() {
        List<Endpoint> result = selector.selectHealthy(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyForNullInput() {
        List<Endpoint> result = selector.selectHealthy(null);
        assertThat(result).isEmpty();
    }
}
