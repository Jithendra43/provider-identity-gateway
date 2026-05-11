package chit.tefca.policy.service;

import chit.tefca.policy.dto.PolicyRuleDto;
import chit.tefca.policy.model.PolicyRule;
import chit.tefca.policy.model.PolicyRuleVersion;
import chit.tefca.policy.repository.PolicyRuleRepository;
import chit.tefca.policy.repository.PolicyRuleVersionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Admin service for policy rule CRUD with automatic versioning.
 */
@Service
@RequiredArgsConstructor
public class PolicyAdminService {

    private static final Logger log = LoggerFactory.getLogger(PolicyAdminService.class);

    private final PolicyRuleRepository ruleRepository;
    private final PolicyRuleVersionRepository versionRepository;

    @Transactional(readOnly = true)
    public List<PolicyRule> listActiveRules() {
        return ruleRepository.findByActiveTrueOrderByPriorityAsc();
    }

    @Transactional(readOnly = true)
    public List<PolicyRule> listAllRules() {
        return ruleRepository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.ASC, "priority"));
    }

    @Transactional(readOnly = true)
    public List<PolicyRule> listRulesByCategory(String category) {
        return ruleRepository.findByCategoryAndActiveTrueOrderByPriorityAsc(category);
    }

    @Transactional
    public PolicyRule setRuleActive(String ruleId, boolean active) {
        PolicyRule rule = getRule(ruleId);
        rule.setActive(active);
        rule = ruleRepository.save(rule);
        log.info("Set policy rule {} active={}", ruleId, active);
        return rule;
    }

    @Transactional(readOnly = true)
    public PolicyRule getRule(String ruleId) {
        return ruleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Policy rule not found: " + ruleId));
    }

    @Transactional
    public PolicyRule createRule(PolicyRuleDto dto) {
        String ruleId = dto.getRuleId() != null ? dto.getRuleId() : generateRuleId(dto.getCategory());

        PolicyRule rule = PolicyRule.builder()
                .ruleId(ruleId)
                .ruleName(dto.getRuleName())
                .category(dto.getCategory())
                .description(dto.getDescription())
                .ruleExpression(dto.getRuleExpression())
                .priority(dto.getPriority())
                .active(dto.isActive())
                .build();

        rule = ruleRepository.save(rule);

        // Create initial version
        PolicyRuleVersion version = PolicyRuleVersion.builder()
                .ruleId(ruleId)
                .versionNumber(1)
                .ruleExpression(dto.getRuleExpression())
                .changedBy(dto.getChangedBy())
                .changeReason("Initial creation")
                .build();
        versionRepository.save(version);

        log.info("Created policy rule {} in category {}", ruleId, dto.getCategory());
        return rule;
    }

    @Transactional
    public PolicyRule updateRule(String ruleId, PolicyRuleDto dto) {
        PolicyRule rule = getRule(ruleId);

        // Determine next version number
        int nextVersion = versionRepository.findTopByRuleIdOrderByVersionNumberDesc(ruleId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        // Update the rule
        rule.setRuleName(dto.getRuleName());
        rule.setCategory(dto.getCategory());
        rule.setDescription(dto.getDescription());
        rule.setRuleExpression(dto.getRuleExpression());
        rule.setPriority(dto.getPriority());
        rule.setActive(dto.isActive());
        rule = ruleRepository.save(rule);

        // Create version entry
        PolicyRuleVersion version = PolicyRuleVersion.builder()
                .ruleId(ruleId)
                .versionNumber(nextVersion)
                .ruleExpression(dto.getRuleExpression())
                .changedBy(dto.getChangedBy())
                .changeReason(dto.getChangeReason())
                .build();
        versionRepository.save(version);

        log.info("Updated policy rule {} to version {}", ruleId, nextVersion);
        return rule;
    }

    @Transactional
    public void deactivateRule(String ruleId) {
        PolicyRule rule = getRule(ruleId);
        rule.setActive(false);
        ruleRepository.save(rule);
        log.info("Deactivated policy rule {}", ruleId);
    }

    @Transactional(readOnly = true)
    public List<PolicyRuleVersion> getRuleHistory(String ruleId) {
        return versionRepository.findByRuleIdOrderByVersionNumberDesc(ruleId);
    }

    private String generateRuleId(String category) {
        return category.toUpperCase().replace(" ", "_") + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
