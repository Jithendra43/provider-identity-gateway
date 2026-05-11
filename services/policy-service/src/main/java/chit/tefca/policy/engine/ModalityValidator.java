package chit.tefca.policy.engine;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates that the requested modality is permitted for the given operation.
 */
@Component
public class ModalityValidator {

    private static final Set<Modality> PA_MODALITIES = EnumSet.of(
            Modality.PA_ORDER_SIGN, Modality.PA_ORDER_SELECT, Modality.PA_APPOINTMENT_BOOK,
            Modality.PA_ORDER_DISPATCH, Modality.PA_ENCOUNTER_START, Modality.PA_ENCOUNTER_DISCHARGE,
            Modality.PA_DTR_QUESTIONNAIRE_PACKAGE, Modality.PA_DTR_QUESTIONNAIRE_READ,
            Modality.PA_DTR_LIBRARY_READ, Modality.PA_DTR_RESPONSE_SUBMIT, Modality.PA_DTR_RESPONSE_READ,
            Modality.PA_CLAIM_SUBMIT, Modality.PA_CLAIM_INQUIRE, Modality.PA_CLAIM_RESPONSE_READ);

    private static final Map<TefcaOperation, Set<Modality>> ALLOWED_MODALITIES = Map.of(
            TefcaOperation.PATIENT_DISCOVERY, Set.of(Modality.XCPD, Modality.FHIR),
            TefcaOperation.DOCUMENT_QUERY, Set.of(Modality.XCA_QUERY, Modality.FHIR),
            TefcaOperation.DOCUMENT_RETRIEVE, Set.of(Modality.XCA_RETRIEVE, Modality.FHIR),
            TefcaOperation.MESSAGE_DELIVERY, Set.of(Modality.XDR, Modality.DIRECT, Modality.FHIR),
            TefcaOperation.FHIR_PROXY, Set.of(Modality.FHIR),
            TefcaOperation.PRIOR_AUTHORIZATION, PA_MODALITIES
    );

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        if (request.getModality() == null) {
            return PolicyExplanation.builder()
                    .ruleId("POLICY-003")
                    .ruleName("Modality and Operation Validation")
                    .category("MODALITY")
                    .result("FAIL")
                    .reason("Modality is not specified")
                    .build();
        }

        Set<Modality> allowed = ALLOWED_MODALITIES.get(request.getOperation());
        if (allowed == null) {
            return PolicyExplanation.builder()
                    .ruleId("POLICY-003")
                    .ruleName("Modality and Operation Validation")
                    .category("MODALITY")
                    .result("FAIL")
                    .reason("Unknown operation " + request.getOperation())
                    .build();
        }

        boolean valid = allowed.contains(request.getModality());

        return PolicyExplanation.builder()
                .ruleId("POLICY-003")
                .ruleName("Modality and Operation Validation")
                .category("MODALITY")
                .result(valid ? "PASS" : "FAIL")
                .reason(valid
                        ? "Modality " + request.getModality() + " is allowed for operation " + request.getOperation()
                        : "Modality " + request.getModality() + " is not allowed for operation " + request.getOperation()
                                + "; allowed: " + allowed)
                .build();
    }
}
