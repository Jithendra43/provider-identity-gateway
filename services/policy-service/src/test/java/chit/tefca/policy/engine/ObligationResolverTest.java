package chit.tefca.policy.engine;

import chit.tefca.common.enums.ExchangePurpose;
import chit.tefca.common.enums.PolicyDecisionType;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ObligationResolverTest {

    private final ObligationResolver resolver = new ObligationResolver();

    @Test
    void deny_emitsNoObligations() {
        assertThat(resolver.resolve(req(ExchangePurpose.TREATMENT), PolicyDecisionType.DENY)).isEmpty();
    }

    @Test
    void permit_alwaysIncludesSecurityRuleBaseline() {
        List<String> out = resolver.resolve(req(ExchangePurpose.TREATMENT), PolicyDecisionType.PERMIT);
        assertThat(out).contains("AUDIT_TRAIL_REQUIRED", "TLS_REQUIRED", "SIGN_RESPONSE");
    }

    @Test
    void permit_attachesPatientHashWhenPatientPresent() {
        PolicyEvaluationRequest r = req(ExchangePurpose.TREATMENT);
        r.setPatientId("PAT-1");
        assertThat(resolver.resolve(r, PolicyDecisionType.PERMIT)).contains("HASH_PATIENT_ID_IN_AUDIT");
    }

    @Test
    void permit_attachesMinimumNecessaryForPayment() {
        assertThat(resolver.resolve(req(ExchangePurpose.PAYMENT), PolicyDecisionType.PERMIT))
                .contains("MINIMUM_NECESSARY");
    }

    @Test
    void permit_attachesIrbForResearch() {
        assertThat(resolver.resolve(req(ExchangePurpose.RESEARCH), PolicyDecisionType.PERMIT))
                .contains("IRB_APPROVAL_REQUIRED", "DE_IDENTIFICATION_REQUIRED");
    }

    @Test
    void permit_attachesPart2NoticeForSudData() {
        PolicyEvaluationRequest r = req(ExchangePurpose.TREATMENT);
        r.setDataClasses(List.of("SUBSTANCE_USE_DISORDER"));
        assertThat(resolver.resolve(r, PolicyDecisionType.PERMIT)).contains("PART2_REDISCLOSURE_NOTICE");
    }

    @Test
    void permit_attachesRedactSsn() {
        PolicyEvaluationRequest r = req(ExchangePurpose.PAYMENT);
        r.setDataClasses(List.of("SSN"));
        assertThat(resolver.resolve(r, PolicyDecisionType.PERMIT)).contains("REDACT_SSN_UNLESS_REQUIRED");
    }

    @Test
    void permit_redactsSensitiveFieldsForNonTreatment() {
        PolicyEvaluationRequest r = req(ExchangePurpose.PAYMENT);
        r.setDataClasses(List.of("HIV", "GENETIC"));
        assertThat(resolver.resolve(r, PolicyDecisionType.PERMIT)).contains("REDACT_SENSITIVE_FIELDS");
    }

    @Test
    void permit_doesNotRedactForTreatment() {
        PolicyEvaluationRequest r = req(ExchangePurpose.TREATMENT);
        r.setDataClasses(List.of("HIV"));
        assertThat(resolver.resolve(r, PolicyDecisionType.PERMIT)).doesNotContain("REDACT_SENSITIVE_FIELDS");
    }

    @Test
    void permit_attachesBreakglassAudit() {
        PolicyEvaluationRequest r = req(ExchangePurpose.EMERGENCY);
        r.setBreakglass(true);
        assertThat(resolver.resolve(r, PolicyDecisionType.PERMIT))
                .contains("BREAKGLASS_AUDIT", "NOTIFY_PRIVACY_OFFICER");
    }

    private PolicyEvaluationRequest req(ExchangePurpose p) {
        return PolicyEvaluationRequest.builder()
                .correlationId("c1")
                .exchangePurpose(p)
                .build();
    }
}
