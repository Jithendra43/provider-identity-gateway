package chit.tefca.policy.engine;

import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Enforces 42 CFR Part 2 (Confidentiality of Substance Use Disorder Patient Records).
 * Records identifying a patient as having received SUD treatment from a Part 2 program
 * may not be disclosed without the patient's written consent that meets §2.31, with
 * narrow medical-emergency / audit / research exceptions.
 *
 * Implementation: when dataClasses contains SUBSTANCE_USE_DISORDER, require
 * consentRefs[PART_2] to be present unless exchangePurpose == EMERGENCY (medical
 * emergency exception under 42 CFR §2.51).
 */
@Component
public class Part2ConsentValidator {

    public static final String DATA_CLASS = "SUBSTANCE_USE_DISORDER";
    public static final String CONSENT_KEY = "PART_2";

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        List<String> classes = request.getDataClasses();
        boolean hasSud = classes != null && classes.contains(DATA_CLASS);

        if (!hasSud) {
            return pass("No 42 CFR Part 2 protected data asserted in request");
        }

        // Medical emergency exception per 42 CFR §2.51 — must be audited.
        if (request.getExchangePurpose() != null
                && "EMERGENCY".equals(request.getExchangePurpose().name())) {
            return pass("Medical emergency exception under 42 CFR §2.51 applies (audit obligation attached)");
        }

        String consent = request.getConsentRefs() == null ? null : request.getConsentRefs().get(CONSENT_KEY);
        if (consent == null || consent.isBlank()) {
            return fail("Substance Use Disorder records require written patient consent meeting 42 CFR §2.31; "
                    + "no PART_2 consent reference supplied");
        }

        return pass("Part 2 consent on file: " + consent);
    }

    private PolicyExplanation pass(String reason) {
        return PolicyExplanation.builder()
                .ruleId("CFR-PART2-SUD")
                .ruleName("42 CFR Part 2 SUD Consent")
                .category("CFR_PART_2")
                .result("PASS")
                .reason(reason)
                .build();
    }

    private PolicyExplanation fail(String reason) {
        return PolicyExplanation.builder()
                .ruleId("CFR-PART2-SUD")
                .ruleName("42 CFR Part 2 SUD Consent")
                .category("CFR_PART_2")
                .result("FAIL")
                .reason(reason)
                .build();
    }
}
