package chit.tefca.policy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyExplanation {
    private String ruleId;
    private String ruleName;
    private String category;
    private String result;   // PASS, FAIL, SKIP
    private String reason;
}
