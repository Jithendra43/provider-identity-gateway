package chit.tefca.policy.engine;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ModalityValidatorTest {

    private final ModalityValidator validator = new ModalityValidator();

    @ParameterizedTest
    @CsvSource({
            "PATIENT_DISCOVERY, XCPD",
            "PATIENT_DISCOVERY, FHIR",
            "DOCUMENT_QUERY, XCA_QUERY",
            "DOCUMENT_QUERY, FHIR",
            "DOCUMENT_RETRIEVE, XCA_RETRIEVE",
            "MESSAGE_DELIVERY, XDR",
            "MESSAGE_DELIVERY, DIRECT",
            "FHIR_PROXY, FHIR"
    })
    void shouldPermitValidModalityOperationCombinations(TefcaOperation op, Modality mod) {
        PolicyExplanation result = validator.validate(
                PolicyEvaluationRequest.builder().operation(op).modality(mod).build());

        assertThat(result.getResult()).isEqualTo("PASS");
    }

    @ParameterizedTest
    @CsvSource({
            "PATIENT_DISCOVERY, XDR",
            "DOCUMENT_QUERY, DIRECT",
            "FHIR_PROXY, XCPD"
    })
    void shouldDenyInvalidModalityOperationCombinations(TefcaOperation op, Modality mod) {
        PolicyExplanation result = validator.validate(
                PolicyEvaluationRequest.builder().operation(op).modality(mod).build());

        assertThat(result.getResult()).isEqualTo("FAIL");
    }

    @Test
    void shouldDenyNullModality() {
        PolicyExplanation result = validator.validate(
                PolicyEvaluationRequest.builder()
                        .operation(TefcaOperation.PATIENT_DISCOVERY)
                        .modality(null)
                        .build());

        assertThat(result.getResult()).isEqualTo("FAIL");
    }
}
