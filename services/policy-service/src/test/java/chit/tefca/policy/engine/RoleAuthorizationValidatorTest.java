package chit.tefca.policy.engine;

import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleAuthorizationValidatorTest {

    private final RoleAuthorizationValidator validator = new RoleAuthorizationValidator();

    @Test
    void shouldPermitValidRole() {
        PolicyExplanation result = validator.validate(
                PolicyEvaluationRequest.builder()
                        .requesterRoles(List.of("CLINICIAN"))
                        .build());

        assertThat(result.getResult()).isEqualTo("PASS");
    }

    @Test
    void shouldPermitWhenAnyRoleIsValid() {
        PolicyExplanation result = validator.validate(
                PolicyEvaluationRequest.builder()
                        .requesterRoles(List.of("UNKNOWN_ROLE", "SYSTEM"))
                        .build());

        assertThat(result.getResult()).isEqualTo("PASS");
    }

    @Test
    void shouldDenyNoRoles() {
        PolicyExplanation result = validator.validate(
                PolicyEvaluationRequest.builder()
                        .requesterRoles(null)
                        .build());

        assertThat(result.getResult()).isEqualTo("FAIL");
    }

    @Test
    void shouldDenyEmptyRoles() {
        PolicyExplanation result = validator.validate(
                PolicyEvaluationRequest.builder()
                        .requesterRoles(List.of())
                        .build());

        assertThat(result.getResult()).isEqualTo("FAIL");
    }

    @Test
    void shouldDenyUnrecognizedRoles() {
        PolicyExplanation result = validator.validate(
                PolicyEvaluationRequest.builder()
                        .requesterRoles(List.of("INTRUDER", "HACKER"))
                        .build());

        assertThat(result.getResult()).isEqualTo("FAIL");
    }
}
