package chit.tefca.policy.engine;

import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DelegationRuleValidatorTest {

    private final DelegationRuleValidator validator = new DelegationRuleValidator();

    @Test
    void shouldSkipWhenNoTargetOrg() {
        PolicyExplanation result = validator.validate(
                PolicyEvaluationRequest.builder()
                        .requesterOrgId("ORG-001")
                        .targetOrgId(null)
                        .build());

        assertThat(result.getResult()).isEqualTo("SKIP");
    }

    @Test
    void shouldPassSameOrgRequest() {
        PolicyExplanation result = validator.validate(
                PolicyEvaluationRequest.builder()
                        .requesterOrgId("ORG-001")
                        .targetOrgId("ORG-001")
                        .build());

        assertThat(result.getResult()).isEqualTo("PASS");
        assertThat(result.getReason()).contains("Same-organization");
    }

    @Test
    void shouldPassCrossOrgDelegation() {
        PolicyExplanation result = validator.validate(
                PolicyEvaluationRequest.builder()
                        .requesterOrgId("ORG-001")
                        .targetOrgId("ORG-002")
                        .build());

        assertThat(result.getResult()).isEqualTo("PASS");
        assertThat(result.getReason()).contains("Cross-org delegation");
    }
}
