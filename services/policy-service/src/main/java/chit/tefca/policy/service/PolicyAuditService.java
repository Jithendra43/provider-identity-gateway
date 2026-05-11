package chit.tefca.policy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyEvaluationResponse;
import chit.tefca.policy.model.PolicyAuditEntry;
import chit.tefca.policy.repository.PolicyAuditRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Persists policy evaluation decisions to the audit trail.
 * Publishing is async to avoid adding latency to the evaluation path.
 */
@Service
@RequiredArgsConstructor
public class PolicyAuditService {

    private static final Logger log = LoggerFactory.getLogger(PolicyAuditService.class);

    private final PolicyAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional
    public void recordDecision(PolicyEvaluationRequest request, PolicyEvaluationResponse response) {
        try {
            String explanationJson = objectMapper.writeValueAsString(response.getExplanations());

            PolicyAuditEntry entry = PolicyAuditEntry.builder()
                    .correlationId(request.getCorrelationId())
                    .requesterOrgId(request.getRequesterOrgId())
                    .targetOrgId(request.getTargetOrgId())
                    .operation(request.getOperation().name())
                    .exchangePurpose(request.getExchangePurpose().name())
                    .decision(response.getDecision().name())
                    .policyVersion(response.getPolicyVersion())
                    .explanationJson(explanationJson)
                    .evaluatedAt(response.getEvaluatedAt())
                    .build();

            auditRepository.save(entry);
            log.debug("Recorded policy audit for correlationId={}", request.getCorrelationId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize policy explanations for audit: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<PolicyAuditEntry> getAuditByCorrelationId(String correlationId) {
        return auditRepository.findByCorrelationId(correlationId);
    }

    @Transactional(readOnly = true)
    public List<PolicyAuditEntry> getAuditByOrg(String orgId, Instant from, Instant to) {
        return auditRepository.findByRequesterOrgIdAndEvaluatedAtBetweenOrderByEvaluatedAtDesc(orgId, from, to);
    }
}
