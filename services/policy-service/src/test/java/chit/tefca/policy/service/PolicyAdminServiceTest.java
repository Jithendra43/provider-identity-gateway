package chit.tefca.policy.service;

import chit.tefca.policy.dto.PolicyRuleDto;
import chit.tefca.policy.model.PolicyRule;
import chit.tefca.policy.model.PolicyRuleVersion;
import chit.tefca.policy.repository.PolicyRuleRepository;
import chit.tefca.policy.repository.PolicyRuleVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyAdminServiceTest {

    @Mock
    private PolicyRuleRepository ruleRepository;

    @Mock
    private PolicyRuleVersionRepository versionRepository;

    @InjectMocks
    private PolicyAdminService adminService;

    @Test
    void shouldListActiveRules() {
        PolicyRule rule = new PolicyRule();
        rule.setRuleId("RULE-1");
        rule.setActive(true);
        when(ruleRepository.findByActiveTrueOrderByPriorityAsc()).thenReturn(List.of(rule));

        List<PolicyRule> result = adminService.listActiveRules();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRuleId()).isEqualTo("RULE-1");
    }

    @Test
    void shouldCreateRuleWithVersion() {
        PolicyRuleDto dto = new PolicyRuleDto();
        dto.setRuleName("Test Rule");
        dto.setCategory("PURPOSE");
        dto.setRuleExpression("purpose IN [TREATMENT]");
        dto.setPriority(50);
        dto.setChangedBy("admin");
        dto.setChangeReason("Initial creation");

        when(ruleRepository.save(any(PolicyRule.class))).thenAnswer(inv -> {
            PolicyRule r = inv.getArgument(0);
            r.setRuleId("RULE-GEN");
            return r;
        });

        PolicyRule result = adminService.createRule(dto);

        assertThat(result.getRuleName()).isEqualTo("Test Rule");
        assertThat(result.getPriority()).isEqualTo(50);
        verify(versionRepository).save(any(PolicyRuleVersion.class));
    }

    @Test
    void shouldUpdateRuleAndIncrementVersion() {
        PolicyRule existing = new PolicyRule();
        existing.setRuleId("RULE-1");
        existing.setRuleName("Old Name");
        existing.setRuleExpression("old expr");
        existing.setPriority(100);
        existing.setActive(true);

        PolicyRuleVersion latestVersion = new PolicyRuleVersion();
        latestVersion.setVersionNumber(2);

        when(ruleRepository.findById("RULE-1")).thenReturn(Optional.of(existing));
        when(versionRepository.findTopByRuleIdOrderByVersionNumberDesc("RULE-1"))
                .thenReturn(Optional.of(latestVersion));
        when(ruleRepository.save(any(PolicyRule.class))).thenAnswer(inv -> inv.getArgument(0));

        PolicyRuleDto dto = new PolicyRuleDto();
        dto.setRuleName("Updated Name");
        dto.setRuleExpression("new expr");
        dto.setPriority(50);
        dto.setChangedBy("admin");
        dto.setChangeReason("Updated");

        PolicyRule result = adminService.updateRule("RULE-1", dto);

        assertThat(result.getRuleName()).isEqualTo("Updated Name");
        assertThat(result.getRuleExpression()).isEqualTo("new expr");

        ArgumentCaptor<PolicyRuleVersion> versionCaptor = ArgumentCaptor.forClass(PolicyRuleVersion.class);
        verify(versionRepository).save(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersionNumber()).isEqualTo(3);
    }

    @Test
    void shouldDeactivateRule() {
        PolicyRule existing = new PolicyRule();
        existing.setRuleId("RULE-1");
        existing.setActive(true);

        when(ruleRepository.findById("RULE-1")).thenReturn(Optional.of(existing));
        when(ruleRepository.save(any(PolicyRule.class))).thenAnswer(inv -> inv.getArgument(0));

        adminService.deactivateRule("RULE-1");

        ArgumentCaptor<PolicyRule> captor = ArgumentCaptor.forClass(PolicyRule.class);
        verify(ruleRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void shouldThrowOnUpdateNonexistentRule() {
        when(ruleRepository.findById("MISSING")).thenReturn(Optional.empty());

        PolicyRuleDto dto = new PolicyRuleDto();

        assertThatThrownBy(() -> adminService.updateRule("MISSING", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MISSING");
    }
}
