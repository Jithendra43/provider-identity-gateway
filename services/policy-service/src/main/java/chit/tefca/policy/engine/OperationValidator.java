package chit.tefca.policy.engine;

import chit.tefca.common.enums.ExchangePurpose;
import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Validates that the requested operation is valid and permitted
 * for the given exchange purpose and modality combination.
 */
@Component
public class OperationValidator {

    // Valid operation-to-modality mappings
    private static final Map<TefcaOperation, Set<Modality>> VALID_MODALITIES = Map.of(
            TefcaOperation.PATIENT_DISCOVERY, Set.of(Modality.XCPD),
            TefcaOperation.DOCUMENT_QUERY, Set.of(Modality.XCA_QUERY, Modality.FHIR),
            TefcaOperation.DOCUMENT_RETRIEVE, Set.of(Modality.XCA_RETRIEVE, Modality.FHIR),
            TefcaOperation.MESSAGE_DELIVERY, Set.of(Modality.XDR, Modality.DIRECT),
            TefcaOperation.FHIR_PROXY, Set.of(Modality.FHIR)
    );

    // Operations not allowed for certain exchange purposes
    private static final Map<ExchangePurpose, Set<TefcaOperation>> RESTRICTED_OPERATIONS = Map.of(
            ExchangePurpose.PUBLIC_HEALTH, Set.of(TefcaOperation.FHIR_PROXY)
    );

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        TefcaOperation operation = request.getOperation();
        Modality modality = request.getModality();
        ExchangePurpose purpose = request.getExchangePurpose();

        if (operation == null) {
            return fail("Operation is required");
        }
        if (modality == null) {
            return fail("Modality is required");
        }

        // Check operation-modality compatibility
        Set<Modality> validModalities = VALID_MODALITIES.get(operation);
        if (validModalities != null && !validModalities.contains(modality)) {
            return fail("Modality " + modality + " is not valid for operation " + operation
                    + ". Valid modalities: " + validModalities);
        }

        // Check purpose-operation restrictions
        if (purpose != null) {
            Set<TefcaOperation> restricted = RESTRICTED_OPERATIONS.get(purpose);
            if (restricted != null && restricted.contains(operation)) {
                return fail("Operation " + operation + " is restricted for exchange purpose " + purpose);
            }
        }

        return pass("Operation " + operation + " with modality " + modality + " is valid");
    }

    private PolicyExplanation pass(String reason) {
        return PolicyExplanation.builder()
                .ruleId("POLICY-010")
                .ruleName("Operation Validation")
                .category("OPERATION")
                .result("PASS")
                .reason(reason)
                .build();
    }

    private PolicyExplanation fail(String reason) {
        return PolicyExplanation.builder()
                .ruleId("POLICY-010")
                .ruleName("Operation Validation")
                .category("OPERATION")
                .result("FAIL")
                .reason(reason)
                .build();
    }
}
