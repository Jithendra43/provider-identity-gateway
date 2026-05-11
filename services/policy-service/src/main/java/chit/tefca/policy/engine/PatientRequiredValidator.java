package chit.tefca.policy.engine;

import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PatientRequiredValidator {

    // PATIENT_DISCOVERY is by definition a search by demographics — no patient
    // ID is known yet. Only post-discovery operations require a patient ID.
    private static final Set<TefcaOperation> PATIENT_REQUIRED_OPS = Set.of(
            TefcaOperation.DOCUMENT_QUERY,
            TefcaOperation.DOCUMENT_RETRIEVE
    );

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        boolean requiresPatient = PATIENT_REQUIRED_OPS.contains(request.getOperation());
        boolean hasPatient = request.getPatientId() != null && !request.getPatientId().isBlank();

        if (!requiresPatient) {
            return PolicyExplanation.builder()
                    .ruleId("POLICY-004")
                    .ruleName("Patient Required Check")
                    .category("PATIENT_REQUIRED")
                    .result("SKIP")
                    .reason("Operation " + request.getOperation() + " does not require patient ID")
                    .build();
        }

        return PolicyExplanation.builder()
                .ruleId("POLICY-004")
                .ruleName("Patient Required Check")
                .category("PATIENT_REQUIRED")
                .result(hasPatient ? "PASS" : "FAIL")
                .reason(hasPatient
                        ? "Patient ID provided for query operation"
                        : "Patient ID is required for operation " + request.getOperation())
                .build();
    }
}
