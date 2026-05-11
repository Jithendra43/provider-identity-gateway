package chit.tefca.policy.service;

import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyEvaluationResponse;
import chit.tefca.policy.engine.PolicyEngine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PolicyEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(PolicyEvaluationService.class);

    private final PolicyEngine policyEngine;
    private final PolicyAuditService auditService;
    private final Timer evaluationTimer;
    private final Counter permitCounter;
    private final Counter denyCounter;

    public PolicyEvaluationService(PolicyEngine policyEngine,
                                   PolicyAuditService auditService,
                                   MeterRegistry meterRegistry) {
        this.policyEngine = policyEngine;
        this.auditService = auditService;
        this.evaluationTimer = meterRegistry.timer("policy.evaluation.duration");
        this.permitCounter = meterRegistry.counter("policy.decisions", "result", "PERMIT");
        this.denyCounter = meterRegistry.counter("policy.decisions", "result", "DENY");
    }

    public PolicyEvaluationResponse evaluate(PolicyEvaluationRequest request) {
        log.info("Evaluating policy for correlationId={} operation={} purpose={}",
                request.getCorrelationId(), request.getOperation(), request.getExchangePurpose());

        PolicyEvaluationResponse response = evaluationTimer.record(() -> policyEngine.evaluate(request));

        // Record metrics
        switch (response.getDecision()) {
            case PERMIT -> permitCounter.increment();
            case DENY -> denyCounter.increment();
            default -> { }
        }

        // Async audit
        auditService.recordDecision(request, response);

        return response;
    }
}
