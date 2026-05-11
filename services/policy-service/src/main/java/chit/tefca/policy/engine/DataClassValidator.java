package chit.tefca.policy.engine;

import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Validates that the requested data class/category is permitted for the given
 * exchange purpose. Different TEFCA exchange purposes have different data scope limits.
 */
@Component
public class DataClassValidator {

    // Operations that can access clinical data
    private static final Set<TefcaOperation> CLINICAL_DATA_OPERATIONS = Set.of(
            TefcaOperation.DOCUMENT_QUERY,
            TefcaOperation.DOCUMENT_RETRIEVE,
            TefcaOperation.FHIR_PROXY
    );

    // Operations that handle administrative/demographics data only
    private static final Set<TefcaOperation> DEMOGRAPHICS_ONLY_OPERATIONS = Set.of(
            TefcaOperation.PATIENT_DISCOVERY
    );

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        TefcaOperation operation = request.getOperation();

        if (operation == null) {
            return fail("Operation is required for data class validation");
        }

        // Patient discovery should not carry clinical payloads
        if (DEMOGRAPHICS_ONLY_OPERATIONS.contains(operation)) {
            return pass("Operation " + operation + " is scoped to demographics data only");
        }

        // Clinical operations are allowed broader data access
        if (CLINICAL_DATA_OPERATIONS.contains(operation)) {
            return pass("Operation " + operation + " is permitted for clinical data access");
        }

        // MESSAGE_DELIVERY — no data class restriction
        return pass("Operation " + operation + " has no data class restrictions");
    }

    private PolicyExplanation pass(String reason) {
        return PolicyExplanation.builder()
                .ruleId("POLICY-009")
                .ruleName("Data Class Validation")
                .category("DATA_CLASS")
                .result("PASS")
                .reason(reason)
                .build();
    }

    private PolicyExplanation fail(String reason) {
        return PolicyExplanation.builder()
                .ruleId("POLICY-009")
                .ruleName("Data Class Validation")
                .category("DATA_CLASS")
                .result("FAIL")
                .reason(reason)
                .build();
    }
}
