package chit.tefca.policy.engine;

import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Enforces 45 CFR §164.508(a)(2): use or disclosure of psychotherapy notes
 * requires a specific written authorization separate from any other consent,
 * with narrow exceptions (originator's own use, oversight of originator,
 * defense in legal action, coroner/ME, and serious-threat per §164.512(j)).
 *
 * Implementation: when dataClasses contains PSYCHOTHERAPY_NOTES, the requester
 * MUST present consentRefs[INDIVIDUAL_AUTH]; otherwise FAIL.
 */
@Component
public class PsychotherapyNotesValidator {

    public static final String DATA_CLASS = "PSYCHOTHERAPY_NOTES";
    public static final String CONSENT_KEY = "INDIVIDUAL_AUTH";

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        List<String> classes = request.getDataClasses();
        boolean hasPsychNotes = classes != null && classes.contains(DATA_CLASS);

        if (!hasPsychNotes) {
            return pass("No psychotherapy notes asserted in request");
        }

        String auth = request.getConsentRefs() == null ? null : request.getConsentRefs().get(CONSENT_KEY);
        if (auth == null || auth.isBlank()) {
            return fail("Psychotherapy notes disclosure requires written individual authorization (45 CFR §164.508(a)(2)); "
                    + "no INDIVIDUAL_AUTH consent reference supplied");
        }

        return pass("Psychotherapy notes authorization on file: " + auth);
    }

    private PolicyExplanation pass(String reason) {
        return PolicyExplanation.builder()
                .ruleId("HIPAA-PR-PSYCHOTHERAPY-NOTES")
                .ruleName("Psychotherapy Notes Authorization (45 CFR §164.508(a)(2))")
                .category("HIPAA_PRIVACY_RULE")
                .result("PASS")
                .reason(reason)
                .build();
    }

    private PolicyExplanation fail(String reason) {
        return PolicyExplanation.builder()
                .ruleId("HIPAA-PR-PSYCHOTHERAPY-NOTES")
                .ruleName("Psychotherapy Notes Authorization (45 CFR §164.508(a)(2))")
                .category("HIPAA_PRIVACY_RULE")
                .result("FAIL")
                .reason(reason)
                .build();
    }
}
