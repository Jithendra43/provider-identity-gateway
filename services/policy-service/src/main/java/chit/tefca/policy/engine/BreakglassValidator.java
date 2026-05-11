package chit.tefca.policy.engine;

import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

/**
 * Enforces TEFCA Common Agreement breakglass procedures (TEFCA SOP — Breakglass).
 * When a requester invokes breakglass, they bypass standard consent gates for
 * an imminent-threat scenario (45 CFR §164.510(b)(3) consistent). The gateway
 * requires:
 *   1. A non-blank justification of at least 20 chars,
 *   2. A patient identifier (so the audit trail is patient-specific),
 *   3. The exchange purpose to be one of the breakglass-eligible purposes.
 * BreakglassValidator does NOT itself permit the request — it gates breakglass
 * misuse. The PERMIT path then attaches BREAKGLASS_AUDIT obligation.
 */
@Component
public class BreakglassValidator {

    private static final int MIN_JUSTIFICATION_LENGTH = 20;

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        if (!request.isBreakglass()) {
            return pass("Breakglass not invoked");
        }

        String justification = request.getBreakglassJustification();
        if (justification == null || justification.trim().length() < MIN_JUSTIFICATION_LENGTH) {
            return fail("Breakglass invocation requires a justification of at least "
                    + MIN_JUSTIFICATION_LENGTH + " characters describing the imminent threat");
        }

        if (request.getPatientId() == null || request.getPatientId().isBlank()) {
            return fail("Breakglass invocation requires a patient identifier for audit attribution");
        }

        if (request.getExchangePurpose() == null) {
            return fail("Breakglass invocation requires an exchange purpose");
        }
        String purpose = request.getExchangePurpose().name();
        if (!purpose.equals("TREATMENT") && !purpose.equals("EMERGENCY") && !purpose.equals("PUBLIC_HEALTH")) {
            return fail("Breakglass is only permitted for TREATMENT, EMERGENCY, or PUBLIC_HEALTH; got " + purpose);
        }

        return pass("Breakglass invocation valid: purpose=" + purpose
                + ", patient=" + request.getPatientId()
                + ", justification=" + justification.length() + " chars");
    }

    private PolicyExplanation pass(String reason) {
        return PolicyExplanation.builder()
                .ruleId("TEFCA-BREAKGLASS")
                .ruleName("TEFCA Breakglass Invocation")
                .category("TEFCA_BREAKGLASS")
                .result("PASS")
                .reason(reason)
                .build();
    }

    private PolicyExplanation fail(String reason) {
        return PolicyExplanation.builder()
                .ruleId("TEFCA-BREAKGLASS")
                .ruleName("TEFCA Breakglass Invocation")
                .category("TEFCA_BREAKGLASS")
                .result("FAIL")
                .reason(reason)
                .build();
    }
}
