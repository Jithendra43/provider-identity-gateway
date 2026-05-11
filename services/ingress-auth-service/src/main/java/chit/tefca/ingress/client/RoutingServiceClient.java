package chit.tefca.ingress.client;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.common.security.HmacRequestSigner;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * WebClient-based REST client for the Routing Service.
 * Internal requests are signed with HMAC-SHA256 for service-to-service authentication.
 */
@Slf4j
@Component
public class RoutingServiceClient {

    private final WebClient webClient;
    private final Duration timeout;
    private final String hmacSecret;
    private final ObjectMapper objectMapper;

    public RoutingServiceClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${tefca.services.routing-url:http://localhost:8082}") String baseUrl,
            @Value("${tefca.timeouts.routing-ms:2000}") long timeoutMs,
            @Value("${tefca.hmac.secret:}") String hmacSecret) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.timeout = Duration.ofMillis(timeoutMs);
        this.hmacSecret = hmacSecret;
        this.objectMapper = objectMapper;
    }

    public RouteResult route(String correlationId, TefcaOperation operation,
                             Modality modality, String targetOrgId,
                             String requesterOrgId, String idempotencyKey,
                             Map<String, Object> payload) {
        return doRoute(buildRequest(correlationId, operation, modality, targetOrgId,
                requesterOrgId, idempotencyKey, payload, null, null, null, null));
    }

    /**
     * Prior Authorization variant. Carries the raw inbound HTTP body, method,
     * downstream Bearer token, and any per-modality headers all the way
     * through the routing service so {@code TransactionForwarder} can
     * replay them verbatim against CRD/DTR/PAS.
     */
    public RouteResult routePa(String correlationId, Modality modality,
                               String targetOrgId, String requesterOrgId,
                               String idempotencyKey, String rawBody,
                               String httpMethod, String downstreamAuthorization,
                               Map<String, String> additionalHeaders) {
        return doRoute(buildRequest(correlationId, TefcaOperation.PRIOR_AUTHORIZATION,
                modality, targetOrgId, requesterOrgId, idempotencyKey, null,
                rawBody, httpMethod, downstreamAuthorization, additionalHeaders));
    }

    private Map<String, Object> buildRequest(String correlationId, TefcaOperation operation,
                                             Modality modality, String targetOrgId,
                                             String requesterOrgId, String idempotencyKey,
                                             Map<String, Object> payload,
                                             String rawBody, String httpMethod,
                                             String downstreamAuthorization,
                                             Map<String, String> additionalHeaders) {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("correlationId", correlationId);
        request.put("operation", operation.name());
        request.put("modality", modality.name());
        request.put("targetOrgId", targetOrgId != null ? targetOrgId : "");
        request.put("requesterOrgId", requesterOrgId);
        if (idempotencyKey != null)         request.put("idempotencyKey", idempotencyKey);
        if (payload != null)                request.put("payload", payload);
        if (rawBody != null)                request.put("rawBody", rawBody);
        if (httpMethod != null)             request.put("httpMethod", httpMethod);
        if (downstreamAuthorization != null)request.put("downstreamAuthorization", downstreamAuthorization);
        if (additionalHeaders != null && !additionalHeaders.isEmpty())
            request.put("additionalHeaders", additionalHeaders);
        return request;
    }

    private RouteResult doRoute(Map<String, Object> request) {
        String correlationId = (String) request.get("correlationId");
        String path = "/api/v1/routing/route";

        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String bodyJson = objectMapper.writeValueAsString(request);

            WebClient.RequestHeadersSpec<?> spec;
            if (hmacSecret != null && !hmacSecret.isBlank()) {
                String signature = HmacRequestSigner.sign(hmacSecret, timestamp, path, bodyJson);
                spec = webClient.post()
                        .uri(path)
                        .header(HmacRequestSigner.HEADER_TIMESTAMP, timestamp)
                        .header(HmacRequestSigner.HEADER_SIGNATURE, signature)
                        .bodyValue(request);
            } else {
                spec = webClient.post()
                        .uri(path)
                        .bodyValue(request);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) spec
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(timeout);

            if (body == null) {
                log.error("Null response from routing service for correlationId={}", correlationId);
                return RouteResult.failure(correlationId, 503, "No response from routing service");
            }

            int httpStatus = body.get("httpStatus") != null ? ((Number) body.get("httpStatus")).intValue() : 200;
            @SuppressWarnings("unchecked")
            Map<String, Object> responsePayload = (Map<String, Object>) body.get("responsePayload");

            return new RouteResult(
                    correlationId,
                    (String) body.get("resolvedEndpointUrl"),
                    (String) body.get("resolvedNodeId"),
                    httpStatus,
                    responsePayload,
                    body.get("routingDurationMs") != null ? ((Number) body.get("routingDurationMs")).longValue() : 0,
                    body.get("forwardDurationMs") != null ? ((Number) body.get("forwardDurationMs")).longValue() : 0
            );
        } catch (Exception e) {
            log.error("Routing service call failed for correlationId={}: {}", correlationId, e.getMessage());
            return RouteResult.failure(correlationId, 503, "Routing service unavailable: " + e.getMessage());
        }
    }

    public record RouteResult(
            String correlationId,
            String resolvedEndpointUrl,
            String resolvedNodeId,
            int httpStatus,
            Map<String, Object> responsePayload,
            long routingDurationMs,
            long forwardDurationMs
    ) {
        public boolean isSuccess() {
            return httpStatus >= 200 && httpStatus < 300;
        }

        public static RouteResult failure(String correlationId, int status, String message) {
            return new RouteResult(correlationId, null, null, status, Map.of("error", message), 0, 0);
        }
    }
}
