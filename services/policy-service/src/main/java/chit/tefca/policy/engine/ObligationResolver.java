package chit.tefca.policy.engine;

import chit.tefca.common.enums.ExchangePurpose;
import chit.tefca.common.enums.PolicyDecisionType;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves obligations attached to PERMIT decisions. Obligations are
 * downstream requirements (audit, redaction, consent verification, signature)
 * that the egress / forwarding layer MUST honour before transmitting PHI.
 *
 * Catalogue of obligations emitted by this gateway:
 *   AUDIT_TRAIL_REQUIRED         — every PERMIT (45 CFR §164.312(b))
 *   HASH_PATIENT_ID_IN_AUDIT     — patient identifier MUST be salted-hash
 *                                   in audit log payload
 *   TLS_REQUIRED                 — transmission MUST use TLS 1.2+ (§164.312(e)(1))
 *   SIGN_RESPONSE                — payload MUST carry digital signature for
 *                                   integrity (§164.312(c)(1), §164.312(e)(2)(i))
 *   MINIMUM_NECESSARY            — egress filter MUST limit to declared scope
 *                                   (§164.502(b))
 *   DE_IDENTIFICATION_PREFERRED  — public health: prefer Safe-Harbor data
 *   DE_IDENTIFICATION_REQUIRED   — research: REQUIRED unless IRB waiver
 *   IRB_APPROVAL_REQUIRED        — research disclosures
 *   CONSENT_VERIFICATION         — re-verify at egress
 *   REDACT_PSYCHOTHERAPY_NOTES   — when class present but disclosure scope
 *                                   does not include them
 *   REDACT_SUD_RECORDS           — Part 2 emergency disclosure must be
 *                                   redacted on subsequent audit copies
 *   REDACT_SENSITIVE_FIELDS      — HIV/MH/REPRO/GENETIC may need to be
 *                                   stripped for non-treatment recipients
 *   PART2_REDISCLOSURE_NOTICE    — 42 CFR §2.32 prohibition on redisclosure
 *   REDACT_SSN_UNLESS_REQUIRED   — strip SSN from payload by default
 *   BREAKGLASS_AUDIT             — elevated audit + notify privacy officer
 *   NOTIFY_PRIVACY_OFFICER       — out-of-band breakglass notification
 */
@Component
public class ObligationResolver {

    private static final Map<ExchangePurpose, List<String>> PURPOSE_OBLIGATIONS = Map.of(
            ExchangePurpose.TREATMENT, List.of("AUDIT_TRAIL_REQUIRED"),
            ExchangePurpose.PAYMENT, List.of("AUDIT_TRAIL_REQUIRED", "MINIMUM_NECESSARY"),
            ExchangePurpose.HEALTHCARE_OPERATIONS, List.of("AUDIT_TRAIL_REQUIRED", "MINIMUM_NECESSARY"),
            ExchangePurpose.PUBLIC_HEALTH, List.of("AUDIT_TRAIL_REQUIRED", "DE_IDENTIFICATION_PREFERRED", "MINIMUM_NECESSARY"),
            ExchangePurpose.INDIVIDUAL_ACCESS, List.of("AUDIT_TRAIL_REQUIRED", "CONSENT_VERIFICATION"),
            ExchangePurpose.RESEARCH, List.of("AUDIT_TRAIL_REQUIRED", "IRB_APPROVAL_REQUIRED", "DE_IDENTIFICATION_REQUIRED", "MINIMUM_NECESSARY"),
            ExchangePurpose.EMERGENCY, List.of("AUDIT_TRAIL_REQUIRED")
    );

    public List<String> resolve(PolicyEvaluationRequest request, PolicyDecisionType decision) {
        if (decision == PolicyDecisionType.DENY) {
            return List.of();
        }

        Set<String> obligations = new LinkedHashSet<>();

        // HIPAA Security Rule §164.312 baseline obligations on every PERMIT.
        obligations.add("AUDIT_TRAIL_REQUIRED");
        obligations.add("TLS_REQUIRED");
        obligations.add("SIGN_RESPONSE");
        if (request.getPatientId() != null && !request.getPatientId().isBlank()) {
            obligations.add("HASH_PATIENT_ID_IN_AUDIT");
        }

        // Purpose-specific obligations.
        List<String> purposeObs = PURPOSE_OBLIGATIONS.get(request.getExchangePurpose());
        if (purposeObs != null) {
            obligations.addAll(purposeObs);
        }

        // Sensitive-data redaction obligations (45 CFR §164.502(b), 42 CFR Part 2).
        List<String> classes = request.getDataClasses();
        if (classes != null && !classes.isEmpty()) {
            String purpose = request.getExchangePurpose() == null
                    ? "" : request.getExchangePurpose().name();
            boolean isTreatmentOrSelf = "TREATMENT".equals(purpose) || "INDIVIDUAL_ACCESS".equals(purpose);
            for (String c : classes) {
                switch (c) {
                    case "PSYCHOTHERAPY_NOTES":
                        if (!isTreatmentOrSelf) obligations.add("REDACT_PSYCHOTHERAPY_NOTES");
                        break;
                    case "SUBSTANCE_USE_DISORDER":
                        obligations.add("PART2_REDISCLOSURE_NOTICE");
                        if ("EMERGENCY".equals(purpose)) obligations.add("REDACT_SUD_RECORDS");
                        break;
                    case "HIV":
                    case "MENTAL_HEALTH":
                    case "REPRODUCTIVE_HEALTH":
                    case "GENETIC":
                        if (!isTreatmentOrSelf) obligations.add("REDACT_SENSITIVE_FIELDS");
                        break;
                    case "SSN":
                        obligations.add("REDACT_SSN_UNLESS_REQUIRED");
                        break;
                    default:
                        break;
                }
            }
        }

        // Breakglass elevated-audit obligation (TEFCA SOP-Breakglass).
        if (request.isBreakglass()) {
            obligations.add("BREAKGLASS_AUDIT");
            obligations.add("NOTIFY_PRIVACY_OFFICER");
        }

        return new ArrayList<>(obligations);
    }
}
