package chit.tefca.app.mock;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;

/**
 * Loopback mock for the downstream Prior Authorization services
 * (CRD / DTR / PAS). Serves canned shapes so the full
 * {@code /api/v1/pa/**} → routing → forward → downstream pipeline can
 * be exercised end-to-end inside a single Fargate task.
 *
 * <p>Every request is JWT-validated against the same JWKS the gateway's
 * {@code MockIdpController} publishes. Only tokens issued by
 * {@code tefca-gateway-internal} (i.e. minted by
 * {@code InternalTokenIssuer}) are accepted, ensuring the mock cannot be
 * called directly from the public ALB without going through the gateway
 * routing layer.</p>
 *
 * <p>Disabled in production by default — toggle with
 * {@code tefca.mock-pa.enabled=true}.</p>
 */
@RestController
@RequestMapping("/mock-pa")
@RequiredArgsConstructor
@ConditionalOnProperty(value = "tefca.mock-pa.enabled", havingValue = "true")
public class MockPaController {

        private static final String EXPECTED_ISSUER = "tefca-gateway-internal";
        private static final Logger log = LoggerFactory.getLogger(MockPaController.class);

    private final RSAKey signingKey;
    private ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    @PostConstruct
    void init() {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        JWSKeySelector<SecurityContext> selector = new JWSVerificationKeySelector<>(
                JWSAlgorithm.RS256,
                new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK())));
        processor.setJWSKeySelector(selector);
        this.jwtProcessor = processor;
        log.info("MockPaController initialised — accepting RS256 tokens kid={} iss={}",
                signingKey.getKeyID(), EXPECTED_ISSUER);
    }

    // ── CRD CDS Hooks ───────────────────────────────────────────────────
    @PostMapping(path = "/cds-services/{hook}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cdsHook(@PathVariable("hook") String hook,
                                     @RequestBody(required = false) Map<String, Object> body,
                                     HttpServletRequest request) {
        ResponseEntity<?> denied = requireValidJwt(request);
        if (denied != null) return denied;
        return ResponseEntity.ok(Map.of(
                "cards", List.of(Map.of(
                        "summary",     "Prior Authorization required",
                        "indicator",   "info",
                        "detail",      "Mock CRD response for hook: " + hook,
                        "source",      Map.of("label", "TEFCA Mock CRD", "url",
                                "http://127.0.0.1:8080/mock-pa"),
                        "suggestions", List.of()
                )),
                "systemActions", List.of()
        ));
    }

    // ── DTR FHIR R4 ─────────────────────────────────────────────────────
    @PostMapping(path = "/fhir/Questionnaire/$questionnaire-package",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dtrPackage(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        ResponseEntity<?> denied = requireValidJwt(request);
        if (denied != null) return denied;
        return ResponseEntity.ok(Map.of(
                "resourceType", "Bundle",
                "type",         "collection",
                "id",           UUID.randomUUID().toString(),
                "entry",        List.of(Map.of("resource", Map.of(
                        "resourceType", "Questionnaire",
                        "id",           "mock-q-001",
                        "status",       "active",
                        "title",        "Mock DTR Questionnaire"
                )))
        ));
    }

    @GetMapping(path = "/fhir/Questionnaire", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dtrQuestionnaireRead(HttpServletRequest request) {
        ResponseEntity<?> denied = requireValidJwt(request);
        if (denied != null) return denied;
        return ResponseEntity.ok(Map.of(
                "resourceType", "Questionnaire",
                "id",           "mock-q-001",
                "status",       "active",
                "title",        "Mock DTR Questionnaire"
        ));
    }

    @GetMapping(path = "/fhir/Library", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dtrLibraryRead(HttpServletRequest request) {
        ResponseEntity<?> denied = requireValidJwt(request);
        if (denied != null) return denied;
        return ResponseEntity.ok(Map.of(
                "resourceType", "Library",
                "id",           "mock-lib-001",
                "status",       "active"
        ));
    }

    @PostMapping(path = "/fhir/QuestionnaireResponse",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dtrResponseSubmit(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        ResponseEntity<?> denied = requireValidJwt(request);
        if (denied != null) return denied;
        return ResponseEntity.ok(Map.of(
                "resourceType", "QuestionnaireResponse",
                "id",           UUID.randomUUID().toString(),
                "status",       "completed"
        ));
    }

    @GetMapping(path = "/fhir/QuestionnaireResponse", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> dtrResponseRead(HttpServletRequest request) {
        ResponseEntity<?> denied = requireValidJwt(request);
        if (denied != null) return denied;
        return ResponseEntity.ok(Map.of(
                "resourceType", "QuestionnaireResponse",
                "id",           "mock-qr-001",
                "status",       "completed"
        ));
    }

    // ── PAS FHIR + X12 278 ──────────────────────────────────────────────
    @PostMapping(path = "/fhir/Claim/$submit",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pasClaimSubmit(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        ResponseEntity<?> denied = requireValidJwt(request);
        if (denied != null) return denied;
        return ResponseEntity.ok(claimResponse("queued"));
    }

    @PostMapping(path = "/fhir/Claim/$inquire",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pasClaimInquire(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        ResponseEntity<?> denied = requireValidJwt(request);
        if (denied != null) return denied;
        return ResponseEntity.ok(claimResponse("active"));
    }

    @GetMapping(path = "/fhir/ClaimResponse", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pasClaimResponseRead(HttpServletRequest request) {
        ResponseEntity<?> denied = requireValidJwt(request);
        if (denied != null) return denied;
        return ResponseEntity.ok(claimResponse("active"));
    }

    // ── helpers ─────────────────────────────────────────────────────────
    private static Map<String, Object> claimResponse(String status) {
        return Map.of(
                "resourceType", "ClaimResponse",
                "id",           UUID.randomUUID().toString(),
                "status",       status,
                "outcome",      "complete",
                "disposition",  "Mock PAS response"
        );
    }

    private ResponseEntity<?> requireValidJwt(HttpServletRequest request) {
        String authz = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authz == null || !authz.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "MISSING_TOKEN",
                            "message", "Authorization Bearer token is required"));
        }
        String token = authz.substring("Bearer ".length()).trim();
        try {
            JWTClaimsSet claims = jwtProcessor.process(token, null);
            if (!EXPECTED_ISSUER.equals(claims.getIssuer())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "INVALID_ISSUER",
                                "message", "Token issuer is not " + EXPECTED_ISSUER));
            }
            return null;
        } catch (Exception e) {
            log.warn("MockPaController — JWT validation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "INVALID_TOKEN",
                            "message", "Token validation failed: " + e.getMessage()));
        }
    }
}
