package chit.tefca.policy.engine;

import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Validates requester roles against allowed roles for the requested operation.
 */
@Component
public class RoleAuthorizationValidator {

    private static final Set<String> PERMITTED_ROLES = Set.of(
            "QHIN_ADMIN", "PARTICIPANT_ADMIN", "CLINICIAN",
            "SYSTEM", "AUDITOR", "PATIENT"
    );

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        // PA flow is authenticated by partner mTLS at the gateway, not by a per-user JWT,
        // so no clinician roles are present. The PA exchange purpose itself is the
        // authorization signal — skip role-based authz for PRIOR_AUTHORIZATION traffic.
        if (request.getOperation() == TefcaOperation.PRIOR_AUTHORIZATION) {
            return PolicyExplanation.builder()
                    .ruleId("POLICY-005")
                    .ruleName("Role Authorization")
                    .category("ROLE_AUTHORIZATION")
                    .result("SKIP")
                    .reason("Role authz not applicable for PA mTLS flow")
                    .build();
        }

        List<String> roles = request.getRequesterRoles();

        if (roles == null || roles.isEmpty()) {
            return PolicyExplanation.builder()
                    .ruleId("POLICY-005")
                    .ruleName("Role Authorization")
                    .category("ROLE_AUTHORIZATION")
                    .result("FAIL")
                    .reason("No roles provided in request")
                    .build();
        }

        boolean hasPermittedRole = roles.stream().anyMatch(PERMITTED_ROLES::contains);

        return PolicyExplanation.builder()
                .ruleId("POLICY-005")
                .ruleName("Role Authorization")
                .category("ROLE_AUTHORIZATION")
                .result(hasPermittedRole ? "PASS" : "FAIL")
                .reason(hasPermittedRole
                        ? "Requester has permitted role"
                        : "None of the requester roles " + roles + " are authorized")
                .build();
    }
}
