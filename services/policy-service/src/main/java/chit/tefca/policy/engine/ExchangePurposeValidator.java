package chit.tefca.policy.engine;

import chit.tefca.common.enums.ExchangePurpose;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ExchangePurposeValidator {

    // TEFCA Common Agreement v2 SOP-recognized exchange purposes (XPs).
    // EMERGENCY is recognized as a treatment subtype and is required to invoke
    // the 42 CFR §2.51 medical emergency exception for SUD records.
    // RESEARCH is permitted with §164.512(i) IRB-waiver / authorization obligations.
    private static final Set<ExchangePurpose> ALLOWED_PURPOSES = Set.of(
            ExchangePurpose.TREATMENT,
            ExchangePurpose.PAYMENT,
            ExchangePurpose.HEALTHCARE_OPERATIONS,
            ExchangePurpose.PUBLIC_HEALTH,
            ExchangePurpose.INDIVIDUAL_ACCESS,
            ExchangePurpose.EMERGENCY,
            ExchangePurpose.RESEARCH,
            ExchangePurpose.GOVERNMENT_BENEFITS,
            ExchangePurpose.PRIOR_AUTHORIZATION
    );

    public PolicyExplanation validate(PolicyEvaluationRequest request) {
        boolean allowed = ALLOWED_PURPOSES.contains(request.getExchangePurpose());

        return PolicyExplanation.builder()
                .ruleId("POLICY-001")
                .ruleName("Exchange Purpose Validation")
                .category("EXCHANGE_PURPOSE")
                .result(allowed ? "PASS" : "FAIL")
                .reason(allowed
                        ? "Exchange purpose " + request.getExchangePurpose() + " is permitted"
                        : "Exchange purpose " + request.getExchangePurpose() + " is not allowed")
                .build();
    }
}
