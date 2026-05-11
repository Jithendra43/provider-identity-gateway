package chit.tefca.policy.repository;

import chit.tefca.policy.model.PolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PolicyRuleRepository extends JpaRepository<PolicyRule, String> {

    List<PolicyRule> findByActiveTrueOrderByPriorityAsc();

    List<PolicyRule> findByCategoryAndActiveTrueOrderByPriorityAsc(String category);

    boolean existsByRuleId(String ruleId);
}
