package chit.tefca.policy.engine;

import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Enforces 45 CFR §164.502(a)(5)(ii): a covered entity must not sell PHI
 * without a valid authorization, and §164.508(a)(3): marketing
 * communications require authorization. The TEFCA gateway treats both
 * as hard DENY at the egress boundary regardless of operator role.
 *
 * Implementation: DENY whenever dataClasses contains SALE_OF_PHI or MARKETING.
 * These classifiers are set by the requester (either explicitly via header
 * or inferred from payload metadata such as "purposeOfUse=marketing").
 */
@Component
public class SaleOfPhiValidator {

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        List<String> classes = request.getDataClasses();
        if (classes == null || classes.isEmpty()) {
            return pass("No prohibited use classifiers in request");
        }

        if (classes.contains("SALE_OF_PHI")) {
            return fail("Sale of PHI is prohibited without authorization per 45 CFR §164.502(a)(5)(ii); "
                    + "TEFCA gateway blocks all SALE_OF_PHI transactions at egress");
        }
        if (classes.contains("MARKETING")) {
            return fail("Marketing communications require individual authorization per 45 CFR §164.508(a)(3); "
                    + "TEFCA gateway blocks all MARKETING transactions at egress");
        }

        return pass("Request does not assert SALE_OF_PHI or MARKETING use");
    }

    private PolicyExplanation pass(String reason) {
        return PolicyExplanation.builder()
                .ruleId("HIPAA-PR-SALE-OF-PHI")
                .ruleName("Prohibition on Sale of PHI / Marketing (45 CFR §164.502(a)(5), §164.508(a)(3))")
                .category("HIPAA_PRIVACY_RULE")
                .result("PASS")
                .reason(reason)
                .build();
    }

    private PolicyExplanation fail(String reason) {
        return PolicyExplanation.builder()
                .ruleId("HIPAA-PR-SALE-OF-PHI")
                .ruleName("Prohibition on Sale of PHI / Marketing (45 CFR §164.502(a)(5), §164.508(a)(3))")
                .category("HIPAA_PRIVACY_RULE")
                .result("FAIL")
                .reason(reason)
                .build();
    }
}
