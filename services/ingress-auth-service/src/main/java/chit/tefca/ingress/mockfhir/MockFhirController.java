package chit.tefca.ingress.mockfhir;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-contained mock FHIR + XCA endpoint for end-to-end smoke tests.
 *
 * <p>Mounted on the ingress-auth-service Fargate task. The directory seed
 * (R005) registers loopback URLs (http://127.0.0.1:8080/mock-fhir/...) so
 * routing-service forwards back into this controller, completing the
 * request → resolve → forward → respond loop with no external partner.
 */
@RestController
@RequestMapping(value = "/mock-fhir", produces = MediaType.APPLICATION_JSON_VALUE)
public class MockFhirController {

    @GetMapping("/Patient")
    public ResponseEntity<String> patient() {
        return ResponseEntity.ok("""
                {
                  "resourceType": "Bundle",
                  "type": "searchset",
                  "total": 1,
                  "entry": [{
                    "resource": {
                      "resourceType": "Patient",
                      "id": "mock-patient-1",
                      "name": [{"family": "Doe", "given": ["Jane"]}],
                      "gender": "female",
                      "birthDate": "1980-01-01"
                    }
                  }]
                }
                """);
    }

    @GetMapping("/DocumentReference")
    public ResponseEntity<String> documentReference() {
        return ResponseEntity.ok("""
                {
                  "resourceType": "Bundle",
                  "type": "searchset",
                  "total": 1,
                  "entry": [{
                    "resource": {
                      "resourceType": "DocumentReference",
                      "id": "mock-doc-1",
                      "status": "current",
                      "type": {"coding": [{"system": "http://loinc.org", "code": "34133-9"}]},
                      "content": [{"attachment": {"contentType": "application/pdf",
                                                  "url": "Binary/mock-binary-1"}}]
                    }
                  }]
                }
                """);
    }

    @GetMapping("/Binary/{id}")
    public ResponseEntity<String> binary(@PathVariable String id) {
        return ResponseEntity.ok("""
                {
                  "resourceType": "Binary",
                  "id": "%s",
                  "contentType": "text/plain",
                  "data": "TW9jayBkb2N1bWVudCBjb250ZW50"
                }
                """.formatted(id));
    }

    @GetMapping("/Endpoint")
    public ResponseEntity<String> endpoint() {
        return ResponseEntity.ok("""
                {
                  "resourceType": "Bundle",
                  "type": "searchset",
                  "total": 1,
                  "entry": [{
                    "resource": {
                      "resourceType": "Endpoint",
                      "id": "mock-endpoint-1",
                      "status": "active",
                      "address": "http://127.0.0.1:8080/mock-fhir"
                    }
                  }]
                }
                """);
    }

    @PostMapping("/xcpd")
    public ResponseEntity<String> xcpd() {
        return ResponseEntity.ok("""
                {"status":"OK","operation":"XCPD","subject":"mock-patient-1"}
                """);
    }

    @PostMapping("/xca-q")
    public ResponseEntity<String> xcaQuery() {
        return ResponseEntity.ok("""
                {"status":"OK","operation":"XCA_QUERY","documentCount":1}
                """);
    }

    @PostMapping("/xca-r")
    public ResponseEntity<String> xcaRetrieve() {
        return ResponseEntity.ok("""
                {"status":"OK","operation":"XCA_RETRIEVE","binaryId":"mock-binary-1"}
                """);
    }
}
