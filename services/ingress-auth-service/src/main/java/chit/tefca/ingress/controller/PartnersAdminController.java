package chit.tefca.ingress.controller;

import chit.tefca.ingress.dto.OnboardPartnerRequest;
import chit.tefca.ingress.dto.OnboardPartnerResponse;
import chit.tefca.ingress.dto.SuspendPartnerRequest;
import chit.tefca.ingress.model.Partner;
import chit.tefca.ingress.repository.PartnerRepository;
import chit.tefca.ingress.service.PartnerOnboardingService;
import chit.tefca.ingress.service.PartnerOnboardingService.InvalidCertificateException;
import chit.tefca.ingress.service.PartnerOnboardingService.PartnerAlreadyExistsException;
import chit.tefca.ingress.service.PartnerOnboardingService.PartnerNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin REST API for partner lifecycle management.
 *
 * <p>All endpoints require an authenticated admin session whose authorities
 * include {@code ROLE_QHIN_ADMIN} (configurable via
 * {@code tefca.admin.required-role}). The chain is enforced in two layers:
 * Spring Security's HTTP filter chain matches {@code /api/v1/admin/**} and
 * requires authentication, and {@link PreAuthorize} on each method enforces
 * the role.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/partners")
@RequiredArgsConstructor
public class PartnersAdminController {

    private final PartnerOnboardingService onboardingService;
    private final PartnerRepository partnerRepository;

    @PostMapping
    @PreAuthorize("hasRole('QHIN_ADMIN')")
    public ResponseEntity<OnboardPartnerResponse> onboard(
            @Valid @RequestBody OnboardPartnerRequest request) {
        OnboardPartnerResponse resp = onboardingService.onboard(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @DeleteMapping("/{partnerId}")
    @PreAuthorize("hasRole('QHIN_ADMIN')")
    public ResponseEntity<Void> suspend(
            @PathVariable String partnerId,
            @Valid @RequestBody(required = false) SuspendPartnerRequest request) {
        onboardingService.suspend(partnerId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('QHIN_ADMIN','QHIN_OPERATOR','QHIN_AUDITOR')")
    public List<PartnerSummary> list(@RequestParam(required = false) String status) {
        List<Partner> partners = (status == null || status.isBlank())
                ? partnerRepository.findAll()
                : partnerRepository.findByStatus(status);
        return partners.stream().map(PartnerSummary::from).toList();
    }

    @GetMapping("/{partnerId}")
    @PreAuthorize("hasAnyRole('QHIN_ADMIN','QHIN_OPERATOR','QHIN_AUDITOR')")
    public ResponseEntity<PartnerSummary> get(@PathVariable String partnerId) {
        return partnerRepository.findById(partnerId)
                .map(PartnerSummary::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── error handlers ─────────────────────────────────────────────────

    @ExceptionHandler(PartnerAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleConflict(PartnerAlreadyExistsException ex) {
        log.warn("Partner onboarding conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "partner_exists", "message", ex.getMessage()));
    }

    @ExceptionHandler(PartnerNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(PartnerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "partner_not_found", "message", ex.getMessage()));
    }

    @ExceptionHandler(InvalidCertificateException.class)
    public ResponseEntity<Map<String, String>> handleBadCert(InvalidCertificateException ex) {
        log.warn("Partner onboarding rejected — bad certificate: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "invalid_certificate", "message", ex.getMessage()));
    }

    // ── view DTO ───────────────────────────────────────────────────────

    public record PartnerSummary(String partnerId, String orgId, String name,
                                 String status, String environment) {
        static PartnerSummary from(Partner p) {
            return new PartnerSummary(p.getPartnerId(), p.getOrgId(), p.getName(),
                    p.getStatus(), p.getEnvironment());
        }
    }
}
