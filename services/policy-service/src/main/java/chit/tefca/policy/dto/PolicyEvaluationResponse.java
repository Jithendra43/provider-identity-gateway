package chit.tefca.policy.dto;

import chit.tefca.common.enums.PolicyDecisionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyEvaluationResponse {

    private String correlationId;

    private PolicyDecisionType decision;

    private List<String> obligations;

    private List<PolicyExplanation> explanations;

    private String policyVersion;

    @Builder.Default
    private Instant evaluatedAt = Instant.now();
}
