package chit.tefca.ingress.controller;

import chit.tefca.common.security.SecurityConstants;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Facilitated FHIR proxy — forwards FHIR requests to internal FHIR store (e.g., HealthLake).
 * Stub for Phase 4; full implementation deferred to HealthLake integration phase.
 */
@RestController
@RequestMapping(SecurityConstants.TEFCA_PREFIX + "/fhir-proxy")
public class FhirProxyController {

    @PostMapping("/**")
    public ResponseEntity<String> proxyFhirRequest(@RequestBody(required = false) String body) {
        // Placeholder — will be wired to FHIR proxy service
        return ResponseEntity.ok("{\"status\": \"fhir-proxy-stub\"}");
    }
}
