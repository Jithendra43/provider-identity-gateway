package chit.tefca.policy.engine;

import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Enforces 45 CFR §164.502(b): minimum-necessary standard. The disclosure
 * must be limited to the minimum amount of PHI needed to accomplish the
 * intended purpose. The minimum-necessary standard does NOT apply to:
 *  - disclosures to the individual themselves (INDIVIDUAL_ACCESS),
 *  - disclosures for treatment (TREATMENT),
 *  - disclosures pursuant to authorization, or
 *  - uses required by law.
 *
 * The validator never DENIES on its own; instead it ensures every PERMIT
 * for non-exempt purposes carries a MINIMUM_NECESSARY obligation that the
 * downstream egress filter must enforce. Listed here as a PASS+SKIP
 * explanation so the auditor can see the rule was evaluated.
 */
@Component
public class MinimumNecessaryValidator {

    private static final Set<String> EXEMPT_PURPOSES = Set.of(
            "TREATMENT", "INDIVIDUAL_ACCESS"
    );

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        if (request.getExchangePurpose() == null) {
            return skip("Exchange purpose unknown; minimum-necessary deferred to obligation enforcement");
        }
        String purpose = request.getExchangePurpose().name();
        if (EXEMPT_PURPOSES.contains(purpose)) {
            return pass("Minimum-necessary standard does not apply to " + purpose
                    + " per 45 CFR §164.502(b)(2)");
        }

        List<String> classes = request.getDataClasses();
        int classCount = classes == null ? 0 : classes.size();
        return pass("Minimum-necessary obligation will be attached for purpose=" + purpose
                + "; declared dataClasses=" + classCount);
    }

    private PolicyExplanation pass(String reason) {
        return PolicyExplanation.builder()
                .ruleId("HIPAA-PR-MIN-NECESSARY")
                .ruleName("Minimum Necessary Standard (45 CFR §164.502(b))")
                .category("HIPAA_PRIVACY_RULE")
                .result("PASS")
                .reason(reason)
                .build();
    }

    private PolicyExplanation skip(String reason) {
        return PolicyExplanation.builder()
                .ruleId("HIPAA-PR-MIN-NECESSARY")
                .ruleName("Minimum Necessary Standard (45 CFR §164.502(b))")
                .category("HIPAA_PRIVACY_RULE")
                .result("SKIP")
                .reason(reason)
                .build();
    }
}
