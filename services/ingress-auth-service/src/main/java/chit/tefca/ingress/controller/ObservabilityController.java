package chit.tefca.ingress.controller;

import chit.tefca.ingress.service.ObservabilityService;
import chit.tefca.ingress.service.ObservabilityService.ServiceSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only observability summary aggregated from each downstream service's
 * Prometheus endpoint. See {@link ObservabilityService} for sampling logic.
 */
@RestController
@RequestMapping("/api/v1/admin/observability")
@RequiredArgsConstructor
public class ObservabilityController {

    private final ObservabilityService observability;

    @GetMapping("/timeseries")
    @PreAuthorize("hasAnyRole('QHIN_ADMIN','QHIN_OPERATOR','QHIN_AUDITOR')")
    public List<ServiceSummary> timeseries() {
        return observability.snapshot();
    }
}
