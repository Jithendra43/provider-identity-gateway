package chit.tefca.ingress.client;

import chit.tefca.common.enums.ExchangePurpose;
import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.PolicyDecisionType;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.common.security.HmacRequestSigner;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * WebClient-based REST client for the Policy Service.
 * Internal requests are signed with HMAC-SHA256 for service-to-service authentication.
 */
@Slf4j
@Component
public class PolicyServiceClient {

    private final WebClient webClient;
    private final Duration timeout;
    private final String hmacSecret;
    private final ObjectMapper objectMapper;

    public PolicyServiceClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${tefca.services.policy-url:http://localhost:8081}") String baseUrl,
            @Value("${tefca.timeouts.policy-ms:2000}") long timeoutMs,
            @Value("${tefca.hmac.secret:}") String hmacSecret) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.timeout = Duration.ofMillis(timeoutMs);
        this.hmacSecret = hmacSecret;
        this.objectMapper = objectMapper;
    }

    public PolicyEvalResult evaluate(String correlationId, TefcaOperation operation,
                                     ExchangePurpose purpose, Modality modality,
                                     String orgId, String nodeId, String targetOrgId,
                                     String patientId, List<String> roles) {
        return evaluate(correlationId, operation, purpose, modality, orgId, nodeId,
                targetOrgId, patientId, roles, null);
    }

    /**
     * Extended evaluate carrying HIPAA / TEFCA enforcement context. The {@code context}
     * map MAY contain:
     *   dataClasses              List&lt;String&gt;        sensitive-data classifiers
     *   breakglass               Boolean              breakglass invocation flag
     *   breakglassJustification  String               required when breakglass=true
     *   consentRefs              Map&lt;String,String&gt;  e.g. INDIVIDUAL_AUTH, PART_2, TPO
     *   partnerAttributes        Map&lt;String,Object&gt;  baaOnFile, qhinStatus, commonAgreementSigned
     */
    public PolicyEvalResult evaluate(String correlationId, TefcaOperation operation,
                                     ExchangePurpose purpose, Modality modality,
                                     String orgId, String nodeId, String targetOrgId,
                                     String patientId, List<String> roles,
                                     Map<String, Object> context) {
        java.util.LinkedHashMap<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("correlationId", correlationId);
        request.put("operation", operation.name());
        request.put("exchangePurpose", purpose.name());
        request.put("modality", modality.name());
        request.put("requesterOrgId", orgId);
        request.put("requesterNodeId", nodeId);
        request.put("targetOrgId", targetOrgId != null ? targetOrgId : "");
        request.put("patientId", patientId != null ? patientId : "");
        request.put("requesterRoles", roles != null ? roles : List.of());
        if (context != null && !context.isEmpty()) {
            // forward only the enforcement keys the policy DTO understands
            for (String k : new String[]{"dataClasses", "breakglass", "breakglassJustification",
                    "consentRefs", "partnerAttributes"}) {
                if (context.containsKey(k)) request.put(k, context.get(k));
            }
        }

        String path = "/api/v1/policy/evaluate";

        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String bodyJson = objectMapper.writeValueAsString(request);

            WebClient.RequestHeadersSpec<?> spec = webClient.post()
                    .uri(path)
                    .bodyValue(request);

            if (hmacSecret != null && !hmacSecret.isBlank()) {
                String signature = HmacRequestSigner.sign(hmacSecret, timestamp, path, bodyJson);
                spec = webClient.post()
                        .uri(path)
                        .header(HmacRequestSigner.HEADER_TIMESTAMP, timestamp)
                        .header(HmacRequestSigner.HEADER_SIGNATURE, signature)
                        .bodyValue(request);
            }

            Map<String, Object> body = spec
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(timeout);

            if (body == null) {
                log.error("Null response from policy service for correlationId={}", correlationId);
                return PolicyEvalResult.deny(correlationId, "No response from policy service");
            }

            String decision = (String) body.get("decision");
            @SuppressWarnings("unchecked")
            List<String> obligations = (List<String>) body.get("obligations");

            return new PolicyEvalResult(
                    correlationId,
                    PolicyDecisionType.valueOf(decision),
                    obligations != null ? obligations : List.of(),
                    (String) body.get("policyVersion")
            );
        } catch (Exception e) {
            String details = e.getMessage();
            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
                details = wcre.getStatusCode() + " body=" + wcre.getResponseBodyAsString();
            }
            log.error("Policy service call failed for correlationId={}: {}", correlationId, details);
            return PolicyEvalResult.deny(correlationId, "Policy service unavailable: " + details);
        }
    }

    public record PolicyEvalResult(
            String correlationId,
            PolicyDecisionType decision,
            List<String> obligations,
            String policyVersion
    ) {
        public boolean isPermitted() {
            return decision == PolicyDecisionType.PERMIT;
        }

        public static PolicyEvalResult deny(String correlationId, String reason) {
            return new PolicyEvalResult(correlationId, PolicyDecisionType.DENY, List.of(), null);
        }
    }
}
