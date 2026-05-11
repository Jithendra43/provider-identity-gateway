package chit.tefca.policy.engine;

import chit.tefca.policy.client.DirectoryCacheClient;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates that the requester organization exists and is active
 * by querying the directory-cache-service.
 */
@Component
@RequiredArgsConstructor
public class RequesterOrgValidator {

    private static final Logger log = LoggerFactory.getLogger(RequesterOrgValidator.class);

    private final DirectoryCacheClient directoryCacheClient;

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        String orgId = request.getRequesterOrgId();

        if (orgId == null || orgId.isBlank()) {
            return PolicyExplanation.builder()
                    .ruleId("POLICY-002")
                    .ruleName("Requester Organization Validation")
                    .category("REQUESTER_ORG")
                    .result("FAIL")
                    .reason("Requester org is missing")
                    .build();
        }

        boolean active;
        try {
            active = directoryCacheClient.isOrganizationActive(orgId);
        } catch (Exception e) {
            log.warn("Failed to query directory-cache for org {}: {}", orgId, e.getMessage());
            // Fail-open: if directory is unreachable, allow but note it
            return PolicyExplanation.builder()
                    .ruleId("POLICY-002")
                    .ruleName("Requester Organization Validation")
                    .category("REQUESTER_ORG")
                    .result("PASS")
                    .reason("Directory service unavailable; org " + orgId + " allowed with degraded validation")
                    .build();
        }

        return PolicyExplanation.builder()
                .ruleId("POLICY-002")
                .ruleName("Requester Organization Validation")
                .category("REQUESTER_ORG")
                .result(active ? "PASS" : "FAIL")
                .reason(active
                        ? "Requester org " + orgId + " is active in directory"
                        : "Requester org " + orgId + " is not active or not found in directory")
                .build();
    }
}
