package chit.tefca.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyRuleDto {

    private String ruleId;

    @NotBlank
    private String ruleName;

    @NotBlank
    private String category;

    private String description;

    @NotBlank
    private String ruleExpression;

    @NotNull
    @Builder.Default
    private Integer priority = 100;

    @Builder.Default
    private boolean active = true;

    private String changedBy;
    private String changeReason;
}
