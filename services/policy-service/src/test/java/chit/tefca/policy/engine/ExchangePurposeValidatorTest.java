package chit.tefca.policy.engine;

import chit.tefca.common.enums.ExchangePurpose;
import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangePurposeValidatorTest {

    private final ExchangePurposeValidator validator = new ExchangePurposeValidator();

    @ParameterizedTest
    @EnumSource(value = ExchangePurpose.class, names = {
            "TREATMENT", "PAYMENT", "HEALTHCARE_OPERATIONS", "PUBLIC_HEALTH",
            "INDIVIDUAL_ACCESS", "EMERGENCY", "RESEARCH", "GOVERNMENT_BENEFITS"
    })
    void shouldPermitAllowedPurposes(ExchangePurpose purpose) {
        PolicyEvaluationRequest request = PolicyEvaluationRequest.builder()
                .exchangePurpose(purpose)
                .build();

        PolicyExplanation result = validator.validate(request);

        assertThat(result.getResult()).isEqualTo("PASS");
    }

    @ParameterizedTest
    @EnumSource(value = ExchangePurpose.class, names = {
            "OTHER", "LAW_ENFORCEMENT", "JUDICIAL_ADMINISTRATIVE",
            "ORGAN_DONATION", "HEALTH_OVERSIGHT", "WORKERS_COMPENSATION",
            "FACILITY_DIRECTORY", "NEXT_OF_KIN"
    })
    void shouldDenyDisallowedPurposes(ExchangePurpose purpose) {
        PolicyEvaluationRequest request = PolicyEvaluationRequest.builder()
                .exchangePurpose(purpose)
                .build();

        PolicyExplanation result = validator.validate(request);

        assertThat(result.getResult()).isEqualTo("FAIL");
    }

    @Test
    void shouldReturnCorrectCategory() {
        PolicyEvaluationRequest request = PolicyEvaluationRequest.builder()
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .build();

        PolicyExplanation result = validator.validate(request);

        assertThat(result.getCategory()).isEqualTo("EXCHANGE_PURPOSE");
        assertThat(result.getRuleId()).isEqualTo("POLICY-001");
    }
}
