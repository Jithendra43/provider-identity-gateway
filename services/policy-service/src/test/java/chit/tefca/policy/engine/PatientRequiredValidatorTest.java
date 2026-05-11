package chit.tefca.policy.engine;

import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PatientRequiredValidatorTest {

    private final PatientRequiredValidator validator = new PatientRequiredValidator();

    @ParameterizedTest
    @EnumSource(value = TefcaOperation.class, names = {
            "DOCUMENT_QUERY", "DOCUMENT_RETRIEVE"
    })
    void shouldRequirePatientForQueryOperations(TefcaOperation op) {
        PolicyExplanation result = validator.validate(
                PolicyEvaluationRequest.builder().operation(op).patientId(null).build());

        assertThat(result.getResult()).isEqualTo("FAIL");
    }

    @ParameterizedTest
    @EnumSource(value = TefcaOperation.class, names = {
            "DOCUMENT_QUERY", "DOCUMENT_RETRIEVE"
    })
    void shouldPassWhenPatientProvidedForQueryOps(TefcaOperation op) {
        PolicyExplanation result = validator.validate(
                PolicyEvaluationRequest.builder().operation(op).patientId("P-123").build());

        assertThat(result.getResult()).isEqualTo("PASS");
    }

    @ParameterizedTest
    @EnumSource(value = TefcaOperation.class, names = {
            "PATIENT_DISCOVERY", "MESSAGE_DELIVERY", "FHIR_PROXY"
    })
    void shouldSkipForNonQueryOperations(TefcaOperation op) {
        PolicyExplanation result = validator.validate(
                PolicyEvaluationRequest.builder().operation(op).patientId(null).build());

        assertThat(result.getResult()).isEqualTo("SKIP");
    }
}
