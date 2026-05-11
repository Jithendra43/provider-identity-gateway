package chit.tefca.policy.controller;

import chit.tefca.common.security.SecurityConstants;
import chit.tefca.policy.dto.PolicyRuleDto;
import chit.tefca.policy.model.PolicyAuditEntry;
import chit.tefca.policy.model.PolicyRule;
import chit.tefca.policy.model.PolicyRuleVersion;
import chit.tefca.policy.repository.PolicyAuditRepository;
import chit.tefca.policy.service.PolicyAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping(SecurityConstants.ADMIN_PREFIX + "/policy")
@RequiredArgsConstructor
public class PolicyAdminController {

    private final PolicyAdminService adminService;
    private final PolicyAuditRepository auditRepository;

    @GetMapping("/rules")
    public ResponseEntity<List<PolicyRule>> listRules(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        if (category != null) {
            return ResponseEntity.ok(adminService.listRulesByCategory(category));
        }
        if (includeInactive) {
            return ResponseEntity.ok(adminService.listAllRules());
        }
        return ResponseEntity.ok(adminService.listActiveRules());
    }

    @PostMapping("/rules/{ruleId}/activate")
    public ResponseEntity<PolicyRule> activateRule(@PathVariable String ruleId) {
        return ResponseEntity.ok(adminService.setRuleActive(ruleId, true));
    }

    @PostMapping("/rules/{ruleId}/deactivate")
    public ResponseEntity<PolicyRule> deactivateRulePost(@PathVariable String ruleId) {
        return ResponseEntity.ok(adminService.setRuleActive(ruleId, false));
    }

    @GetMapping("/rules/{ruleId}")
    public ResponseEntity<PolicyRule> getRule(@PathVariable String ruleId) {
        return ResponseEntity.ok(adminService.getRule(ruleId));
    }

    @PostMapping("/rules")
    public ResponseEntity<PolicyRule> createRule(@Valid @RequestBody PolicyRuleDto dto) {
        PolicyRule created = adminService.createRule(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<PolicyRule> updateRule(
            @PathVariable String ruleId,
            @Valid @RequestBody PolicyRuleDto dto) {
        return ResponseEntity.ok(adminService.updateRule(ruleId, dto));
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> deactivateRule(@PathVariable String ruleId) {
        adminService.deactivateRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rules/{ruleId}/history")
    public ResponseEntity<List<PolicyRuleVersion>> getRuleHistory(@PathVariable String ruleId) {
        return ResponseEntity.ok(adminService.getRuleHistory(ruleId));
    }

    @GetMapping("/audit-entries")
    public ResponseEntity<Page<PolicyAuditEntry>> listAuditEntries(
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String requesterOrgId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) Instant since,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<PolicyAuditEntry> result = auditRepository.search(
                correlationId, decision, requesterOrgId, operation, since,
                PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "evaluatedAt")));
        return ResponseEntity.ok(result);
    }
}
