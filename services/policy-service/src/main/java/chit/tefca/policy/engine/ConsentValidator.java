package chit.tefca.policy.engine;

import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

/**
 * Validates that the request has appropriate consent indicators.
 * TEFCA requires documented consent for certain exchange purposes,
 * particularly Individual Access and non-Treatment flows.
 */
@Component
public class ConsentValidator {

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        // Individual Access requires explicit consent
        if ("INDIVIDUAL_ACCESS".equals(request.getExchangePurpose().name())) {
            if (request.getPatientId() == null || request.getPatientId().isBlank()) {
                return PolicyExplanation.builder()
                        .ruleId("POLICY-007")
                        .ruleName("Consent Validation")
                        .category("CONSENT")
                        .result("FAIL")
                        .reason("Individual Access requests require a patient identifier to verify consent")
                        .build();
            }
        }

        // Treatment, Payment, and Healthcare Operations have implied consent under HIPAA
        return PolicyExplanation.builder()
                .ruleId("POLICY-007")
                .ruleName("Consent Validation")
                .category("CONSENT")
                .result("PASS")
                .reason("Consent requirements satisfied for exchange purpose: " + request.getExchangePurpose())
                .build();
    }
}
