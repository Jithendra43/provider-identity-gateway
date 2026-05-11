package chit.tefca.policy.repository;

import chit.tefca.policy.model.PolicyRuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyRuleVersionRepository extends JpaRepository<PolicyRuleVersion, Long> {

    List<PolicyRuleVersion> findByRuleIdOrderByVersionNumberDesc(String ruleId);

    Optional<PolicyRuleVersion> findTopByRuleIdOrderByVersionNumberDesc(String ruleId);
}
