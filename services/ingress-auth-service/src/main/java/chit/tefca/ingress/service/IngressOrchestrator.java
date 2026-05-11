package chit.tefca.ingress.service;

import chit.tefca.common.audit.AuditEvent;
import chit.tefca.common.audit.AuditPublisher;
import chit.tefca.common.dto.TefcaRequest;
import chit.tefca.common.dto.TefcaResponse;
import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.common.model.RequesterIdentity;
import chit.tefca.common.correlation.CorrelationIdHolder;
import chit.tefca.ingress.client.PolicyServiceClient;
import chit.tefca.ingress.client.PolicyServiceClient.PolicyEvalResult;
import chit.tefca.ingress.client.RoutingServiceClient;
import chit.tefca.ingress.client.RoutingServiceClient.RouteResult;
import chit.tefca.ingress.config.AdminProperties;
import chit.tefca.ingress.dto.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Core orchestrator for the Ingress/Auth service.
 * Flow: Authenticate → Normalize → Policy Check → Route Resolve → Forward → Respond
 */
@Slf4j
@Service
public class IngressOrchestrator {

    private final PolicyServiceClient policyClient;
    private final RoutingServiceClient routingClient;
    private final RequestNormalizer requestNormalizer;
    private final AccessLogService accessLogService;
    private final AuditPublisher auditPublisher;
    private final AdminProperties adminProperties;
    private final Timer orchestrationTimer;
    private final Counter successCounter;
    private final Counter policyDeniedCounter;
    private final Counter routingFailureCounter;

    public IngressOrchestrator(PolicyServiceClient policyClient,
                               RoutingServiceClient routingClient,
                               RequestNormalizer requestNormalizer,
                               AccessLogService accessLogService,
                               AuditPublisher auditPublisher,
                               AdminProperties adminProperties,
                               MeterRegistry meterRegistry) {
        this.policyClient = policyClient;
        this.routingClient = routingClient;
        this.requestNormalizer = requestNormalizer;
        this.accessLogService = accessLogService;
        this.auditPublisher = auditPublisher;
        this.adminProperties = adminProperties;
        this.orchestrationTimer = Timer.builder("ingress.orchestration.duration")
                .description("Ingress orchestration duration")
                .register(meterRegistry);
        this.successCounter = Counter.builder("ingress.orchestration.success")
                .description("Successful orchestrations")
                .register(meterRegistry);
        this.policyDeniedCounter = Counter.builder("ingress.orchestration.policy_denied")
                .description("Policy denied orchestrations")
                .register(meterRegistry);
        this.routingFailureCounter = Counter.builder("ingress.orchestration.routing_failure")
                .description("Routing failures")
                .register(meterRegistry);
    }

    public TefcaResponse processPatientDiscovery(PatientDiscoveryRequest request) {
        return processPatientDiscovery(request, null);
    }

    public TefcaResponse processPatientDiscovery(PatientDiscoveryRequest request, RequesterIdentity identity) {
        return processPatientDiscovery(request, identity, null);
    }

    public TefcaResponse processPatientDiscovery(PatientDiscoveryRequest request, RequesterIdentity identity,
                                                 Map<String, Object> hipaaContext) {
        RequesterIdentity resolved = resolveIdentity(identity);
        TefcaRequest normalized = requestNormalizer.normalizePatientDiscovery(request, resolved.getOrgId(), resolved.getNodeId());
        return orchestrate(normalized, TefcaOperation.PATIENT_DISCOVERY, Modality.XCPD, resolved, hipaaContext);
    }

    public TefcaResponse processDocumentQuery(DocumentQueryRequest request) {
        return processDocumentQuery(request, null);
    }

    public TefcaResponse processDocumentQuery(DocumentQueryRequest request, RequesterIdentity identity) {
        return processDocumentQuery(request, identity, null);
    }

    public TefcaResponse processDocumentQuery(DocumentQueryRequest request, RequesterIdentity identity,
                                              Map<String, Object> hipaaContext) {
        RequesterIdentity resolved = resolveIdentity(identity);
        TefcaRequest normalized = requestNormalizer.normalizeDocumentQuery(request, resolved.getOrgId(), resolved.getNodeId());
        return orchestrate(normalized, TefcaOperation.DOCUMENT_QUERY, Modality.XCA_QUERY, resolved, hipaaContext);
    }

    public TefcaResponse processDocumentRetrieve(DocumentRetrieveRequest request) {
        return processDocumentRetrieve(request, null);
    }

    public TefcaResponse processDocumentRetrieve(DocumentRetrieveRequest request, RequesterIdentity identity) {
        return processDocumentRetrieve(request, identity, null);
    }

    public TefcaResponse processDocumentRetrieve(DocumentRetrieveRequest request, RequesterIdentity identity,
                                                 Map<String, Object> hipaaContext) {
        RequesterIdentity resolved = resolveIdentity(identity);
        String orgId = resolved.getOrgId();
        String nodeId = resolved.getNodeId();
        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : java.util.UUID.randomUUID().toString();
        java.util.Map<String, Object> drPayload = new java.util.LinkedHashMap<>();
        if (request.getExchangePurpose() != null) drPayload.put("exchangePurpose", request.getExchangePurpose().name());
        if (request.getDocumentId()    != null) drPayload.put("documentId",    request.getDocumentId());
        if (request.getRepositoryId()  != null) drPayload.put("repositoryId",  request.getRepositoryId());
        if (request.getPatientId()     != null) drPayload.put("patientId",     request.getPatientId());
        if (request.getTargetOrgId()   != null) drPayload.put("targetOrgId",   request.getTargetOrgId());
        TefcaRequest normalized = TefcaRequest.builder()
                .correlationId(correlationId)
                .operation(TefcaOperation.DOCUMENT_RETRIEVE)
                .exchangePurpose(request.getExchangePurpose())
                .modality(Modality.XCA_RETRIEVE)
                .requesterOrgId(orgId)
                .requesterNodeId(nodeId)
                .targetOrgId(request.getTargetOrgId())
                .documentId(request.getDocumentId())
                .patientId(request.getPatientId())
                .payload(drPayload)
                .build();
        return orchestrate(normalized, TefcaOperation.DOCUMENT_RETRIEVE, Modality.XCA_RETRIEVE, resolved, hipaaContext);
    }

    public TefcaResponse processMessageDelivery(MessageDeliveryRequest request) {
        return processMessageDelivery(request, null);
    }

    public TefcaResponse processMessageDelivery(MessageDeliveryRequest request, RequesterIdentity identity) {
        return processMessageDelivery(request, identity, null);
    }

    public TefcaResponse processMessageDelivery(MessageDeliveryRequest request, RequesterIdentity identity,
                                                Map<String, Object> hipaaContext) {
        RequesterIdentity resolved = resolveIdentity(identity);
        String orgId = resolved.getOrgId();
        String nodeId = resolved.getNodeId();
        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : java.util.UUID.randomUUID().toString();
        TefcaRequest normalized = TefcaRequest.builder()
                .correlationId(correlationId)
                .operation(TefcaOperation.MESSAGE_DELIVERY)
                .exchangePurpose(request.getExchangePurpose())
                .modality(Modality.XDR)
                .requesterOrgId(orgId)
                .requesterNodeId(nodeId)
                .targetOrgId(request.getTargetOrgId())
                .payload(request.getMessageBody())
                .build();
        return orchestrate(normalized, TefcaOperation.MESSAGE_DELIVERY, Modality.XDR, resolved, hipaaContext);
    }

    /**
     * Container for a forwarded Prior Authorization response.
     *
     * <p>Unlike the TEFCA flows we do <i>not</i> wrap the downstream payload
     * in a {@link TefcaResponse} envelope: CRD/DTR/PAS responses are
     * standardised FHIR/CDS Hooks shapes and the partner expects them
     * verbatim. This record carries the http status, the parsed JSON body
     * (when present), the policy obligations the gateway enforced, and the
     * correlation id for observability.</p>
     */
    public record PaResult(
            String correlationId,
            int httpStatus,
            Map<String, Object> body,
            List<String> obligations,
            String resolvedEndpointUrl,
            String resolvedNodeId,
            long durationMs,
            String errorCode
    ) {
        public boolean isSuccess() { return httpStatus >= 200 && httpStatus < 300; }
    }

    /**
     * End-to-end Prior Authorization processing.
     *
     * <ol>
     *   <li>Run policy evaluation as
     *       {@code (operation=PRIOR_AUTHORIZATION, purpose=PRIOR_AUTHORIZATION,
     *       modality=&lt;PA modality&gt;, targetOrgId=ORG-CHIT-PA-PLATFORM)}.
     *       Rule {@code HIPAA-PR-PA-PERMITTED} (priority 110) attaches the
     *       MINIMUM_NECESSARY + AUDIT_TRAIL_REQUIRED obligations.</li>
     *   <li>Mint a 60s internal RS256 JWT bound to the proven provider
     *       org id and the correlation id.</li>
     *   <li>Hand the raw inbound body, the JWT, and the per-modality
     *       headers to the routing service which selects the matching
     *       endpoint from the 14-row PA endpoint table and forwards.</li>
     *   <li>Audit the call in either outcome.</li>
     * </ol>
     *
     * @param modality       PA_* modality matching the CRD/DTR/PAS endpoint.
     * @param providerOrgId  org id resolved from the inbound mTLS cert
     *                       ({@link chit.tefca.ingress.filter.MtlsOrgIdentityFilter}).
     * @param rawBody        verbatim inbound HTTP body (CDS Hooks JSON, FHIR Bundle, …).
     * @param httpMethod     inbound HTTP method ({@code POST} or {@code GET}).
     * @param internalJwt    the bearer token to attach as the downstream Authorization.
     */
    public PaResult processPa(Modality modality, String providerOrgId,
                              String rawBody, String httpMethod, String internalJwt,
                              Map<String, String> additionalHeaders) {
        if (!modality.isPa()) {
            throw new IllegalArgumentException("Modality is not a PA modality: " + modality);
        }
        String correlationId = CorrelationIdHolder.get() != null
                ? CorrelationIdHolder.get() : java.util.UUID.randomUUID().toString();
        String targetOrgId = "ORG-CHIT-PA-PLATFORM";

        return orchestrationTimer.record(() -> {
            // PA flow auths off the inbound mTLS cert, which resolves to an org but not a
            // specific operator node. Synthesize a stable per-org node id so the policy
            // engine's @NotBlank requesterNodeId validation passes; downstream HIPAA rules
            // key off org+modality, not nodeId, so any deterministic value is fine.
            String requesterNodeId = providerOrgId + ":PA-MTLS";

            // 1) Policy
            PolicyEvalResult policyResult = policyClient.evaluate(
                    correlationId, TefcaOperation.PRIOR_AUTHORIZATION,
                    chit.tefca.common.enums.ExchangePurpose.PRIOR_AUTHORIZATION,
                    modality, providerOrgId, requesterNodeId, targetOrgId, null,
                    List.of(), null);

            if (!policyResult.isPermitted()) {
                policyDeniedCounter.increment();
                log.warn("PA policy denied for correlationId={} modality={}", correlationId, modality);
                accessLogService.logAccess(correlationId, "PRIOR_AUTHORIZATION",
                        "/api/v1/pa/" + modality.name(), providerOrgId, 403, 0);
                publishPaAuditEvent(correlationId, modality, providerOrgId, targetOrgId,
                        "DENIED", policyResult.decision().name(), policyResult.obligations());
                return new PaResult(correlationId, 403,
                        Map.of("error", "POLICY_DENIED",
                                "message", "Prior Authorization request denied by policy engine"),
                        policyResult.obligations(), null, null, 0, "POLICY_DENIED");
            }

            // 2) Route + forward verbatim
            RouteResult routeResult = routingClient.routePa(
                    correlationId, modality, targetOrgId, providerOrgId,
                    /*idempotencyKey*/ null, rawBody, httpMethod,
                    internalJwt, additionalHeaders);

            long total = routeResult.routingDurationMs() + routeResult.forwardDurationMs();
            int status = routeResult.httpStatus();
            if (!routeResult.isSuccess()) {
                routingFailureCounter.increment();
                log.error("PA routing failed for correlationId={} status={}", correlationId, status);
                accessLogService.logAccess(correlationId, "PRIOR_AUTHORIZATION",
                        "/api/v1/pa/" + modality.name(), providerOrgId, status, total);
                publishPaAuditEvent(correlationId, modality, providerOrgId, targetOrgId,
                        "ERROR", "ROUTING_FAILURE", policyResult.obligations());
                return new PaResult(correlationId, status,
                        routeResult.responsePayload(), policyResult.obligations(),
                        routeResult.resolvedEndpointUrl(), routeResult.resolvedNodeId(),
                        total, "ROUTING_FAILURE");
            }

            // 3) Success
            successCounter.increment();
            accessLogService.logAccess(correlationId, "PRIOR_AUTHORIZATION",
                    "/api/v1/pa/" + modality.name(), providerOrgId, status, total);
            publishPaAuditEvent(correlationId, modality, providerOrgId, targetOrgId,
                    "SUCCESS", "PERMIT", policyResult.obligations());
            return new PaResult(correlationId, status,
                    routeResult.responsePayload(), policyResult.obligations(),
                    routeResult.resolvedEndpointUrl(), routeResult.resolvedNodeId(),
                    total, null);
        });
    }

    private void publishPaAuditEvent(String correlationId, Modality modality,
                                     String providerOrgId, String targetOrgId,
                                     String outcome, String policyDecision,
                                     List<String> obligations) {
        java.util.LinkedHashMap<String, String> meta = new java.util.LinkedHashMap<>();
        meta.put("modality", modality.name());
        if (obligations != null && !obligations.isEmpty()) {
            meta.put("obligations", String.join(",", obligations));
        }
        auditPublisher.publish(AuditEvent.builder()
                .correlationId(correlationId)
                .eventType("INGRESS_PA_" + modality.name())
                .operation(TefcaOperation.PRIOR_AUTHORIZATION)
                .requesterOrgId(providerOrgId)
                .targetOrgId(targetOrgId)
                .outcome(outcome)
                .policyDecision(policyDecision)
                .metadata(meta)
                .build());
    }

    /**
     * Returns a non-null {@link RequesterIdentity} suitable for forwarding to the policy engine.
     *
     * <p>JWT-authenticated TEFCA requests already carry a populated identity (set by
     * {@link chit.tefca.ingress.filter.JwtAuthenticationFilter}). Admin-console
     * requests authenticated via Cognito session do not, because the Cognito ID token
     * carries no orgId/nodeId/roles claims. In that case the configured
     * {@code tefca.admin.test-harness} identity is used so the policy engine sees a
     * real, directory-resolvable requester instead of {@code "unknown"}.
     */
    private RequesterIdentity resolveIdentity(RequesterIdentity identity) {
        AdminProperties.TestHarness harness = adminProperties.getTestHarness();
        String orgId  = identity != null && identity.getOrgId()  != null && !identity.getOrgId().isBlank()
                ? identity.getOrgId()  : harness.getOrgId();
        String nodeId = identity != null && identity.getNodeId() != null && !identity.getNodeId().isBlank()
                ? identity.getNodeId() : harness.getNodeId();
        java.util.List<String> roles = identity != null && identity.getRoles() != null && !identity.getRoles().isEmpty()
                ? identity.getRoles() : harness.getRoles();
        String subject = identity != null && identity.getSubject() != null
                ? identity.getSubject() : "admin-console";
        return RequesterIdentity.builder()
                .subject(subject)
                .orgId(orgId)
                .nodeId(nodeId)
                .roles(roles)
                .build();
    }

    private TefcaResponse orchestrate(TefcaRequest normalized, TefcaOperation operation,
                                       Modality modality, RequesterIdentity identity,
                                       Map<String, Object> hipaaContext) {
        String correlationId = normalized.getCorrelationId();
        log.info("Orchestrating {} for correlationId={}", operation, correlationId);

        return orchestrationTimer.record(() -> {
            // Step 1: Policy evaluation (HIPAA / TEFCA enforcement context attached).
            PolicyEvalResult policyResult = policyClient.evaluate(
                    correlationId, operation, normalized.getExchangePurpose(),
                    modality, normalized.getRequesterOrgId(), normalized.getRequesterNodeId(),
                    normalized.getTargetOrgId(), normalized.getPatientId(),
                    identity != null ? identity.getRoles() : List.of(),
                    hipaaContext
            );

            if (!policyResult.isPermitted()) {
                policyDeniedCounter.increment();
                log.warn("Policy denied for correlationId={}, decision={}", correlationId, policyResult.decision());
                accessLogService.logAccess(correlationId, operation.name(), 
                        "/api/v1/tefca/" + operation.name().toLowerCase(),
                        normalized.getRequesterOrgId(), 403, 0);
                publishAuditEvent(correlationId, operation, normalized, "DENIED", policyResult.decision().name(),
                        List.of(), hipaaContext);
                return TefcaResponse.builder()
                        .correlationId(correlationId)
                        .status("DENIED")
                        .obligations(policyResult.obligations())
                        .errors(List.of(TefcaResponse.ErrorDetail.builder()
                                .code("POLICY_DENIED")
                                .message("Request denied by policy engine")
                                .build()))
                        .build();
            }

            // Step 2: Routing + forwarding
            RouteResult routeResult = routingClient.route(
                    correlationId, operation, modality,
                    normalized.getTargetOrgId(), normalized.getRequesterOrgId(),
                    normalized.getIdempotencyKey(), normalized.getPayload()
            );

            if (!routeResult.isSuccess()) {
                routingFailureCounter.increment();
                log.error("Routing failed for correlationId={}, status={}", correlationId, routeResult.httpStatus());
                accessLogService.logAccess(correlationId, operation.name(),
                        "/api/v1/tefca/" + operation.name().toLowerCase(),
                        normalized.getRequesterOrgId(), routeResult.httpStatus(), 
                        routeResult.routingDurationMs() + routeResult.forwardDurationMs());
                publishAuditEvent(correlationId, operation, normalized, "ERROR", "ROUTING_FAILURE",
                        policyResult.obligations(), hipaaContext);
                return TefcaResponse.builder()
                        .correlationId(correlationId)
                        .status("ERROR")
                        .errors(List.of(TefcaResponse.ErrorDetail.builder()
                                .code("ROUTING_FAILURE")
                                .message("Failed to route request")
                                .build()))
                        .metadata(Map.of("httpStatus", String.valueOf(routeResult.httpStatus())))
                        .build();
            }

            // Success
            successCounter.increment();
            long totalDuration = routeResult.routingDurationMs() + routeResult.forwardDurationMs();
            accessLogService.logAccess(correlationId, operation.name(),
                    "/api/v1/tefca/" + operation.name().toLowerCase(),
                    normalized.getRequesterOrgId(), 200, totalDuration);
            publishAuditEvent(correlationId, operation, normalized, "SUCCESS", "PERMIT",
                    policyResult.obligations(), hipaaContext);

            return TefcaResponse.builder()
                    .correlationId(correlationId)
                    .status("SUCCESS")
                    .data(routeResult.responsePayload())
                    .obligations(policyResult.obligations())
                    .metadata(Map.of(
                            "resolvedEndpoint", routeResult.resolvedEndpointUrl() != null ? routeResult.resolvedEndpointUrl() : "",
                            "resolvedNodeId", routeResult.resolvedNodeId() != null ? routeResult.resolvedNodeId() : "",
                            "routingDurationMs", String.valueOf(routeResult.routingDurationMs()),
                            "forwardDurationMs", String.valueOf(routeResult.forwardDurationMs())
                    ))
                    .build();
        });
    }

    private void publishAuditEvent(String correlationId, TefcaOperation operation,
                                    TefcaRequest normalized, String outcome, String policyDecision,
                                    List<String> obligations, Map<String, Object> hipaaContext) {
        java.util.LinkedHashMap<String, String> meta = new java.util.LinkedHashMap<>();
        if (obligations != null && !obligations.isEmpty()) meta.put("obligations", String.join(",", obligations));
        if (hipaaContext != null) {
            Object dc = hipaaContext.get("dataClasses");
            if (dc instanceof List<?> dcList && !dcList.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < dcList.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(dcList.get(i));
                }
                meta.put("dataClasses", sb.toString());
            }
            if (Boolean.TRUE.equals(hipaaContext.get("breakglass"))) {
                meta.put("breakglass", "true");
                Object bgr = hipaaContext.get("breakglassJustification");
                if (bgr != null) meta.put("breakglassJustification", bgr.toString());
            }
            Object cr = hipaaContext.get("consentRefs");
            if (cr instanceof Map<?, ?> crMap && !crMap.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                int i = 0;
                for (Map.Entry<?, ?> e : crMap.entrySet()) {
                    if (i++ > 0) sb.append(';');
                    sb.append(e.getKey()).append('=').append(e.getValue());
                }
                meta.put("consentRefs", sb.toString());
            }
        }
        // Hash patient id when present (HIPAA Security Rule §164.312(b) audit + safe-harbor).
        if (normalized.getPatientId() != null && !normalized.getPatientId().isBlank()) {
            meta.put("patientIdHash", PatientIdHasher.hash(normalized.getPatientId()));
        }
        auditPublisher.publish(AuditEvent.builder()
                .correlationId(correlationId)
                .eventType("INGRESS_" + operation.name())
                .operation(operation)
                .requesterOrgId(normalized.getRequesterOrgId())
                .requesterNodeId(normalized.getRequesterNodeId())
                .targetOrgId(normalized.getTargetOrgId())
                .outcome(outcome)
                .policyDecision(policyDecision)
                .metadata(meta)
                .build());
    }
}
