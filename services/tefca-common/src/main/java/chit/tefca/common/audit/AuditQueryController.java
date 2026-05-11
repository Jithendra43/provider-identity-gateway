package chit.tefca.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Read-only audit query endpoint for the admin UI. Auto-mounted in any
 * service whose Spring scan picks up tefca-common AND has the audit JPA
 * repository wired (i.e. ingress-auth-service after EntityScan was widened).
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
public class AuditQueryController {

    private final AuditEventRepository repository;

    public AuditQueryController(AuditEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/events")
    public ResponseEntity<Page<AuditEventEntity>> search(
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String requesterOrgId,
            @RequestParam(required = false) Instant since,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<AuditEventEntity> result = repository.search(
                correlationId, eventType, outcome, requesterOrgId, since,
                PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(result);
    }
}
