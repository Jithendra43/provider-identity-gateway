package chit.tefca.policy.engine;

import chit.tefca.common.enums.PolicyDecisionType;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyEvaluationResponse;
import chit.tefca.policy.dto.PolicyExplanation;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Core policy engine using chain-of-responsibility pattern.
 * Each validator produces an explanation; a single FAIL results in DENY.
 * ObligationResolver attaches post-decision obligations to PERMIT results.
 */
@Component
@RequiredArgsConstructor
public class PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);

    private final ExchangePurposeValidator exchangePurposeValidator;
    private final RequesterOrgValidator requesterOrgValidator;
    private final ModalityValidator modalityValidator;
    private final PatientRequiredValidator patientRequiredValidator;
    private final RoleAuthorizationValidator roleAuthorizationValidator;
    private final DelegationRuleValidator delegationRuleValidator;
    private final ConsentValidator consentValidator;
    private final TimeWindowValidator timeWindowValidator;
    private final DataClassValidator dataClassValidator;
    private final OperationValidator operationValidator;
    private final ObligationResolver obligationResolver;
    // HIPAA / TEFCA enforcement validators
    private final SaleOfPhiValidator saleOfPhiValidator;
    private final PsychotherapyNotesValidator psychotherapyNotesValidator;
    private final Part2ConsentValidator part2ConsentValidator;
    private final BaaValidator baaValidator;
    private final BreakglassValidator breakglassValidator;
    private final MinimumNecessaryValidator minimumNecessaryValidator;

    public PolicyEvaluationResponse evaluate(PolicyEvaluationRequest request) {
        List<PolicyExplanation> explanations = new ArrayList<>();
        boolean denied = false;
        // Hard-DENY HIPAA Privacy Rule guards run first (fail-fast at egress).
        explanations.add(saleOfPhiValidator.validate(request));
        explanations.add(psychotherapyNotesValidator.validate(request));
        explanations.add(part2ConsentValidator.validate(request));
        explanations.add(baaValidator.validate(request));
        explanations.add(breakglassValidator.validate(request));
        // Existing exchange / role / consent / data-class checks.
        explanations.add(operationValidator.validate(request));
        explanations.add(exchangePurposeValidator.validate(request));
        explanations.add(requesterOrgValidator.validate(request));
        explanations.add(modalityValidator.validate(request));
        explanations.add(patientRequiredValidator.validate(request));
        explanations.add(roleAuthorizationValidator.validate(request));
        explanations.add(delegationRuleValidator.validate(request));
        explanations.add(consentValidator.validate(request));
        explanations.add(timeWindowValidator.validate(request));
        explanations.add(dataClassValidator.validate(request));
        explanations.add(minimumNecessaryValidator.validate(request));

        for (PolicyExplanation explanation : explanations) {
            if ("FAIL".equals(explanation.getResult())) {
                denied = true;
                break;
            }
        }

        PolicyDecisionType decision = denied ? PolicyDecisionType.DENY : PolicyDecisionType.PERMIT;
        List<String> obligations = obligationResolver.resolve(request, decision);

        log.info("Policy decision for correlationId={}: {} with {} obligations",
                request.getCorrelationId(), decision, obligations.size());

        return PolicyEvaluationResponse.builder()
                .correlationId(request.getCorrelationId())
                .decision(decision)
                .explanations(explanations)
                .obligations(obligations)
                .policyVersion("1.0.0")
                .build();
    }
}
