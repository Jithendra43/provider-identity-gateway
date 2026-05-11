package chit.tefca.ingress.controller;

import chit.tefca.common.dto.TefcaResponse;
import chit.tefca.common.enums.Modality;
import chit.tefca.ingress.filter.MtlsOrgIdentityFilter;
import chit.tefca.ingress.security.InternalTokenIssuer;
import chit.tefca.ingress.service.IngressOrchestrator;
import chit.tefca.ingress.service.IngressOrchestrator.PaResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP entry point for the Prior Authorization (PA) flow.
 *
 * <p>Inbound paths exactly match the C-HIT PA Platform integration
 * contract — the dashboard / SMART app posts to these URLs, and the
 * gateway forwards to the corresponding upstream CRD / DTR / PAS
 * services. Bodies are forwarded verbatim and the upstream response is
 * placed in {@link TefcaResponse#getData()} unchanged so callers can
 * read CDS Hook cards or FHIR resources directly.</p>
 *
 * <p>Inbound auth chain: ALB mTLS → {@link chit.tefca.ingress.filter.MtlsValidationFilter}
 * → {@link MtlsOrgIdentityFilter} (resolves provider org id from the cert
 * thumbprint). Outbound auth: a 60-second RS256 JWT minted by
 * {@link InternalTokenIssuer} (iss=tefca-gateway-internal) on the
 * Authorization header so the downstream service can validate trust via
 * the gateway JWKS.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/pa")
@RequiredArgsConstructor
@ConditionalOnProperty(value = "tefca.pa.enabled", havingValue = "true", matchIfMissing = true)
public class PaRoutingController {

    private static final String AUD_CRD = "crd-service";
    private static final String AUD_DTR = "dtr-service";
    private static final String AUD_PAS = "pas-service";

    private final IngressOrchestrator orchestrator;
    private final InternalTokenIssuer tokenIssuer;

    // ── CRD — CDS Hooks (6) ─────────────────────────────────────────────
    @PostMapping(path = "/pa-crd",               consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TefcaResponse> orderSign(@RequestBody String body, HttpServletRequest req) {
        return forward(Modality.PA_ORDER_SIGN, body, "POST", AUD_CRD, req, Map.of());
    }

    @PostMapping(path = "/pa-crd-select",        consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TefcaResponse> orderSelect(@RequestBody String body, HttpServletRequest req) {
        return forward(Modality.PA_ORDER_SELECT, body, "POST", AUD_CRD, req, Map.of());
    }

    @PostMapping(path = "/pa-appointment",       consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TefcaResponse> appointmentBook(@RequestBody String body, HttpServletRequest req) {
        return forward(Modality.PA_APPOINTMENT_BOOK, body, "POST", AUD_CRD, req, Map.of());
    }

    @PostMapping(path = "/pa-order-dispatch",    consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TefcaResponse> orderDispatch(@RequestBody String body, HttpServletRequest req) {
        return forward(Modality.PA_ORDER_DISPATCH, body, "POST", AUD_CRD, req, Map.of());
    }

    @PostMapping(path = "/pa-encounter-start",   consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TefcaResponse> encounterStart(@RequestBody String body, HttpServletRequest req) {
        return forward(Modality.PA_ENCOUNTER_START, body, "POST", AUD_CRD, req, Map.of());
    }

    @PostMapping(path = "/pa-encounter-discharge", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TefcaResponse> encounterDischarge(@RequestBody String body, HttpServletRequest req) {
        return forward(Modality.PA_ENCOUNTER_DISCHARGE, body, "POST", AUD_CRD, req, Map.of());
    }

    // ── DTR — FHIR R4 (5) ───────────────────────────────────────────────
    @PostMapping(path = "/dtr/questionnaire-package", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TefcaResponse> dtrPackage(@RequestBody String body, HttpServletRequest req) {
        return forward(Modality.PA_DTR_QUESTIONNAIRE_PACKAGE, body, "POST", AUD_DTR, req, Map.of());
    }

    @GetMapping("/dtr/questionnaire/{id}")
    public ResponseEntity<TefcaResponse> dtrQuestionnaire(@PathVariable("id") String id, HttpServletRequest req) {
        return forward(Modality.PA_DTR_QUESTIONNAIRE_READ, "", "GET", AUD_DTR, req,
                Map.of("X-Resource-Id", id));
    }

    @GetMapping("/dtr/library/{id}")
    public ResponseEntity<TefcaResponse> dtrLibrary(@PathVariable("id") String id, HttpServletRequest req) {
        return forward(Modality.PA_DTR_LIBRARY_READ, "", "GET", AUD_DTR, req,
                Map.of("X-Resource-Id", id));
    }

    @PostMapping(path = "/dtr/questionnaire-response", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TefcaResponse> dtrResponseSubmit(@RequestBody String body, HttpServletRequest req) {
        return forward(Modality.PA_DTR_RESPONSE_SUBMIT, body, "POST", AUD_DTR, req, Map.of());
    }

    @GetMapping("/dtr/questionnaire-response/{id}")
    public ResponseEntity<TefcaResponse> dtrResponseRead(@PathVariable("id") String id, HttpServletRequest req) {
        return forward(Modality.PA_DTR_RESPONSE_READ, "", "GET", AUD_DTR, req,
                Map.of("X-Resource-Id", id));
    }

    // ── PAS — Da Vinci PAS (3) ──────────────────────────────────────────
    @PostMapping(path = "/pas/claim-submit",  consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TefcaResponse> claimSubmit(@RequestBody String body, HttpServletRequest req) {
        return forward(Modality.PA_CLAIM_SUBMIT, body, "POST", AUD_PAS, req, Map.of());
    }

    @PostMapping(path = "/pas/claim-inquire", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TefcaResponse> claimInquire(@RequestBody String body, HttpServletRequest req) {
        return forward(Modality.PA_CLAIM_INQUIRE, body, "POST", AUD_PAS, req, Map.of());
    }

    @GetMapping("/pas/claim-response/{id}")
    public ResponseEntity<TefcaResponse> claimResponseRead(@PathVariable("id") String id, HttpServletRequest req) {
        return forward(Modality.PA_CLAIM_RESPONSE_READ, "", "GET", AUD_PAS, req,
                Map.of("X-Resource-Id", id));
    }

    // ── shared forward ──────────────────────────────────────────────────
    private ResponseEntity<TefcaResponse> forward(Modality modality, String body, String method,
                                                  String audience, HttpServletRequest req,
                                                  Map<String, String> extraHeaders) {
        Object orgIdAttr = req.getAttribute(MtlsOrgIdentityFilter.PROVIDER_ORG_ID_ATTR);
        if (!(orgIdAttr instanceof String providerOrgId) || providerOrgId.isBlank()) {
            log.warn("PA request rejected — providerOrgId attribute missing for modality={}", modality);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(error(modality, null, "PROVIDER_NOT_RESOLVED",
                            "Client certificate did not resolve to a provider organization"));
        }

        String correlationId = req.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String internalJwt = tokenIssuer.mintForService(audience, providerOrgId, correlationId);

        Map<String, String> headers = new LinkedHashMap<>(extraHeaders);
        headers.put("X-Correlation-Id", correlationId);
        headers.put("X-Provider-Org-Id", providerOrgId);
        headers.put("X-PA-Modality", modality.name());

        PaResult result = orchestrator.processPa(modality, providerOrgId, body, method, internalJwt, headers);

        TefcaResponse envelope = TefcaResponse.builder()
                .correlationId(result.correlationId())
                .status(result.isSuccess() ? "SUCCESS" : "ERROR")
                .data(result.body())
                .obligations(result.obligations())
                .errors(result.isSuccess() ? List.of()
                        : List.of(TefcaResponse.ErrorDetail.builder()
                                .code(result.errorCode() != null ? result.errorCode() : "ROUTING_FAILURE")
                                .message("Upstream service returned status " + result.httpStatus())
                                .build()))
                .build();

        ResponseEntity.BodyBuilder rb = ResponseEntity.status(result.httpStatus())
                .header("X-Correlation-Id", result.correlationId());
        if (result.obligations() != null && !result.obligations().isEmpty()) {
            rb.header("X-Policy-Obligations", String.join(",", result.obligations()));
        }
        return rb.contentType(MediaType.APPLICATION_JSON).body(envelope);
    }

    private TefcaResponse error(Modality modality, String correlationId, String code, String message) {
        return TefcaResponse.builder()
                .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
                .status("ERROR")
                .data(Map.of("modality", modality.name()))
                .obligations(List.of())
                .errors(List.of(TefcaResponse.ErrorDetail.builder()
                        .code(code).message(message).build()))
                .build();
    }
}
