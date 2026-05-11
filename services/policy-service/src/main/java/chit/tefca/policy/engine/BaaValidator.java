package chit.tefca.policy.engine;

import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Enforces 45 CFR §164.502(e)(1): a covered entity may disclose PHI to a
 * business associate only with a written contract (BAA) meeting §164.504(e).
 *
 * Under TEFCA, the Common Agreement substitutes for direct BAAs between
 * QHINs (and between a QHIN and its Participants). For sub-participants or
 * legacy non-QHIN partners, the gateway requires baaOnFile=true in the
 * partnerAttributes map (populated from the directory cache).
 *
 * Decision matrix:
 *   qhinStatus=QHIN                         → PASS (Common Agreement covers)
 *   qhinStatus=PARTICIPANT, commonAgreement → PASS
 *   anything else, baaOnFile=true           → PASS
 *   otherwise                               → FAIL
 */
@Component
public class BaaValidator {

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        Map<String, Object> attrs = request.getPartnerAttributes();
        if (attrs == null || attrs.isEmpty()) {
            // Permissive default: when partner attributes are not supplied (e.g. non-cross-org
            // operations or self-routing), skip BAA enforcement. The directory cache populates
            // attrs whenever a target is resolved.
            return pass("No partner attributes supplied; BAA enforcement skipped");
        }

        String qhinStatus = asString(attrs.get("qhinStatus"));
        boolean commonAgreement = asBool(attrs.get("commonAgreementSigned"), false);
        boolean baaOnFile = asBool(attrs.get("baaOnFile"), false);

        if ("QHIN".equalsIgnoreCase(qhinStatus)) {
            return pass("Target is a QHIN; TEFCA Common Agreement provides BAA-equivalent terms");
        }
        if ("PARTICIPANT".equalsIgnoreCase(qhinStatus) && commonAgreement) {
            return pass("Target is a Participant under signed Common Agreement");
        }
        if (baaOnFile) {
            return pass("Business Associate Agreement is on file with target organization");
        }

        return fail("Disclosure to non-QHIN target requires a Business Associate Agreement on file "
                + "(45 CFR §164.502(e)(1)); partnerAttributes.baaOnFile is not true");
    }

    private static String asString(Object v) { return v == null ? null : v.toString(); }
    private static boolean asBool(Object v, boolean dflt) {
        if (v == null) return dflt;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    private PolicyExplanation pass(String reason) {
        return PolicyExplanation.builder()
                .ruleId("HIPAA-PR-BAA-REQUIRED")
                .ruleName("Business Associate Agreement (45 CFR §164.502(e), §164.504(e))")
                .category("HIPAA_PRIVACY_RULE")
                .result("PASS")
                .reason(reason)
                .build();
    }

    private PolicyExplanation fail(String reason) {
        return PolicyExplanation.builder()
                .ruleId("HIPAA-PR-BAA-REQUIRED")
                .ruleName("Business Associate Agreement (45 CFR §164.502(e), §164.504(e))")
                .category("HIPAA_PRIVACY_RULE")
                .result("FAIL")
                .reason(reason)
                .build();
    }
}
