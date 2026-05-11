package chit.tefca.ingress.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@org.springframework.context.annotation.Profile("!prod")
public class HealthController {

    @GetMapping("/actuator/health/liveness")
    public ResponseEntity<Map<String, String>> liveness() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/actuator/health/readiness")
    public ResponseEntity<Map<String, String>> readiness() {
        return ResponseEntity.ok(Map.of("status", "UP", "timestamp", Instant.now().toString()));
    }
}
