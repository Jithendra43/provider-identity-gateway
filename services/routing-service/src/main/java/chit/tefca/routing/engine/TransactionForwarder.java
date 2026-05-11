package chit.tefca.routing.engine;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.model.Endpoint;
import chit.tefca.routing.dto.RouteRequest;
import chit.tefca.routing.dto.RouteResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Forwards a routed transaction to the resolved downstream endpoint.
 *
 * <p>Timeout is selected per call (highest precedence first):</p>
 * <ol>
 *   <li>{@link Endpoint#getTimeoutMs()} if set on the directory row.</li>
 *   <li>10s for {@code PA_CLAIM_*} (PAS X12 278 round-trips can be slow).</li>
 *   <li>5s for any other PA modality (CRD/DTR are interactive — kept tight).</li>
 *   <li>25s default for the legacy XCPD/XCA/XDR/FHIR flows.</li>
 * </ol>
 *
 * <p>For Prior Authorization the gateway forwards the inbound HTTP body
 * verbatim ({@link RouteRequest#getRawBody()}) and may use {@code GET}
 * instead of {@code POST}. The {@link RouteRequest#getDownstreamAuthorization()}
 * field is the short-lived service JWT minted by
 * {@code InternalTokenIssuer}; it goes out as
 * {@code Authorization: Bearer …} so the downstream PA service can verify
 * the call came from the gateway.</p>
 */
@Component
public class TransactionForwarder {

    private static final Logger log = LoggerFactory.getLogger(TransactionForwarder.class);
    private static final Duration LEGACY_DEFAULT_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration PA_CLAIM_DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PA_DEFAULT_TIMEOUT = Duration.ofMillis(450);

    private final WebClient partnerWebClient;
    private final EndpointHealthTracker healthTracker;

    public TransactionForwarder(@Qualifier("partnerWebClient") WebClient partnerWebClient,
                                 EndpointHealthTracker healthTracker) {
        this.partnerWebClient = partnerWebClient;
        this.healthTracker = healthTracker;
    }

    @CircuitBreaker(name = "forwardCircuit", fallbackMethod = "forwardFallback")
    public RouteResponse forward(RouteRequest request, Endpoint endpoint) {
        long start = System.currentTimeMillis();
        Duration timeout = resolveTimeout(request, endpoint);
        HttpMethod method = resolveMethod(request);
        log.info("Forwarding correlationId={} to endpoint={} method={} timeoutMs={}",
                request.getCorrelationId(), endpoint.getUrl(), method, timeout.toMillis());

        try {
            WebClient.RequestBodySpec spec = partnerWebClient
                    .method(method)
                    .uri(endpoint.getUrl())
                    .headers(h -> applyHeaders(h, request));

            WebClient.RequestHeadersSpec<?> bodySpec;
            if (HttpMethod.GET.equals(method) || HttpMethod.DELETE.equals(method)) {
                bodySpec = spec;
            } else if (request.getRawBody() != null && !request.getRawBody().isEmpty()) {
                bodySpec = spec.contentType(MediaType.APPLICATION_JSON).bodyValue(request.getRawBody());
            } else {
                bodySpec = spec.contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request.getPayload() != null ? request.getPayload() : Map.of());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = bodySpec
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(timeout)
                    .block();

            long forwardDuration = System.currentTimeMillis() - start;
            healthTracker.recordSuccess(endpoint.getEndpointId(), endpoint.getUrl(), forwardDuration);

            return RouteResponse.builder()
                    .correlationId(request.getCorrelationId())
                    .resolvedEndpointUrl(endpoint.getUrl())
                    .resolvedNodeId(endpoint.getNodeId())
                    .httpStatus(200)
                    .responsePayload(responseBody)
                    .forwardDurationMs(forwardDuration)
                    .completedAt(Instant.now())
                    .build();

        } catch (WebClientResponseException e) {
            long forwardDuration = System.currentTimeMillis() - start;
            healthTracker.recordFailure(endpoint.getEndpointId(), endpoint.getUrl(), e.getMessage());

            return RouteResponse.builder()
                    .correlationId(request.getCorrelationId())
                    .resolvedEndpointUrl(endpoint.getUrl())
                    .resolvedNodeId(endpoint.getNodeId())
                    .httpStatus(e.getStatusCode().value())
                    .responsePayload(Map.of("error", e.getStatusText()))
                    .forwardDurationMs(forwardDuration)
                    .completedAt(Instant.now())
                    .build();

        } catch (Exception e) {
            long forwardDuration = System.currentTimeMillis() - start;
            healthTracker.recordFailure(endpoint.getEndpointId(), endpoint.getUrl(), e.getMessage());
            throw e;
        }
    }

    private Duration resolveTimeout(RouteRequest request, Endpoint endpoint) {
        if (endpoint.getTimeoutMs() != null && endpoint.getTimeoutMs() > 0) {
            return Duration.ofMillis(endpoint.getTimeoutMs());
        }
        Modality m = request.getModality();
        if (m == null) return LEGACY_DEFAULT_TIMEOUT;
        if (m == Modality.PA_CLAIM_SUBMIT
                || m == Modality.PA_CLAIM_INQUIRE
                || m == Modality.PA_CLAIM_RESPONSE_READ) {
            return PA_CLAIM_DEFAULT_TIMEOUT;
        }
        if (m.isPa()) return PA_DEFAULT_TIMEOUT;
        return LEGACY_DEFAULT_TIMEOUT;
    }

    private HttpMethod resolveMethod(RouteRequest request) {
        String m = request.getHttpMethod();
        if (m == null || m.isBlank()) return HttpMethod.POST;
        return HttpMethod.valueOf(m.toUpperCase());
    }

    private void applyHeaders(HttpHeaders h, RouteRequest request) {
        h.set("X-Correlation-Id", request.getCorrelationId());
        if (request.getDownstreamAuthorization() != null && !request.getDownstreamAuthorization().isBlank()) {
            h.set(HttpHeaders.AUTHORIZATION, "Bearer " + request.getDownstreamAuthorization());
        }
        if (request.getAdditionalHeaders() != null) {
            request.getAdditionalHeaders().forEach((k, v) -> {
                if (k != null && v != null && !k.equalsIgnoreCase("Authorization")) {
                    h.set(k, v);
                }
            });
        }
    }

    @SuppressWarnings("unused")
    private RouteResponse forwardFallback(RouteRequest request, Endpoint endpoint, Throwable t) {
        log.error("Circuit breaker open for endpoint={} correlationId={}: {}",
                endpoint.getUrl(), request.getCorrelationId(), t.getMessage());

        return RouteResponse.builder()
                .correlationId(request.getCorrelationId())
                .resolvedEndpointUrl(endpoint.getUrl())
                .resolvedNodeId(endpoint.getNodeId())
                .httpStatus(503)
                .responsePayload(Map.of("error", "SERVICE_UNAVAILABLE", "reason", "Circuit breaker open"))
                .completedAt(Instant.now())
                .build();
    }
}
