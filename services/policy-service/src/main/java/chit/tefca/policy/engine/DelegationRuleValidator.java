package chit.tefca.policy.engine;

import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

/**
 * Validates delegation chains — ensures that if a requester is acting
 * on behalf of a delegating organization, the delegation is valid.
 * In the current implementation delegation is permitted when both
 * requester and target org IDs are present and differ, but a production
 * deployment would verify against a delegation registry.
 */
@Component
public class DelegationRuleValidator {

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        String requesterOrg = request.getRequesterOrgId();
        String targetOrg = request.getTargetOrgId();

        // If no target org specified, delegation check is not applicable
        if (targetOrg == null || targetOrg.isBlank()) {
            return PolicyExplanation.builder()
                    .ruleId("POLICY-006")
                    .ruleName("Delegation Rule Check")
                    .category("DELEGATION")
                    .result("SKIP")
                    .reason("No target organization specified; delegation check not applicable")
                    .build();
        }

        // Same-org request — no delegation needed
        if (requesterOrg.equals(targetOrg)) {
            return PolicyExplanation.builder()
                    .ruleId("POLICY-006")
                    .ruleName("Delegation Rule Check")
                    .category("DELEGATION")
                    .result("PASS")
                    .reason("Same-organization request; no delegation required")
                    .build();
        }

        // Cross-org request — delegation is permitted (real implementation
        // would check a delegation registry / trust chain)
        return PolicyExplanation.builder()
                .ruleId("POLICY-006")
                .ruleName("Delegation Rule Check")
                .category("DELEGATION")
                .result("PASS")
                .reason("Cross-org delegation from " + requesterOrg + " to " + targetOrg + " is permitted")
                .build();
    }
}
