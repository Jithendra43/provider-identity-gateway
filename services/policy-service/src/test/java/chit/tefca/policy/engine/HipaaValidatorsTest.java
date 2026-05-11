package chit.tefca.policy.engine;

import chit.tefca.common.enums.ExchangePurpose;
import chit.tefca.policy.dto.PolicyEvaluationRequest;
import chit.tefca.policy.dto.PolicyExplanation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HipaaValidatorsTest {

    private final SaleOfPhiValidator saleOfPhi = new SaleOfPhiValidator();
    private final PsychotherapyNotesValidator psychNotes = new PsychotherapyNotesValidator();
    private final Part2ConsentValidator part2 = new Part2ConsentValidator();
    private final BaaValidator baa = new BaaValidator();
    private final BreakglassValidator breakglass = new BreakglassValidator();
    private final MinimumNecessaryValidator minNec = new MinimumNecessaryValidator();

    private PolicyEvaluationRequest base() {
        return PolicyEvaluationRequest.builder()
                .correlationId("c1")
                .exchangePurpose(ExchangePurpose.TREATMENT)
                .build();
    }

    @Test
    void saleOfPhi_blocksMarketing() {
        PolicyEvaluationRequest r = base();
        r.setDataClasses(List.of("MARKETING"));
        assertThat(saleOfPhi.validate(r).getResult()).isEqualTo("FAIL");
    }

    @Test
    void saleOfPhi_blocksSale() {
        PolicyEvaluationRequest r = base();
        r.setDataClasses(List.of("SALE_OF_PHI"));
        assertThat(saleOfPhi.validate(r).getResult()).isEqualTo("FAIL");
    }

    @Test
    void saleOfPhi_passesNormalTraffic() {
        PolicyEvaluationRequest r = base();
        r.setDataClasses(List.of("HIV"));
        assertThat(saleOfPhi.validate(r).getResult()).isEqualTo("PASS");
    }

    @Test
    void psychotherapyNotes_blocksWithoutAuth() {
        PolicyEvaluationRequest r = base();
        r.setDataClasses(List.of("PSYCHOTHERAPY_NOTES"));
        PolicyExplanation x = psychNotes.validate(r);
        assertThat(x.getResult()).isEqualTo("FAIL");
        assertThat(x.getReason()).contains("§164.508(a)(2)");
    }

    @Test
    void psychotherapyNotes_passesWithAuth() {
        PolicyEvaluationRequest r = base();
        r.setDataClasses(List.of("PSYCHOTHERAPY_NOTES"));
        r.setConsentRefs(Map.of("INDIVIDUAL_AUTH", "AUTH-1234"));
        assertThat(psychNotes.validate(r).getResult()).isEqualTo("PASS");
    }

    @Test
    void psychotherapyNotes_passesWhenNotPresent() {
        assertThat(psychNotes.validate(base()).getResult()).isEqualTo("PASS");
    }

    @Test
    void part2_blocksWithoutConsent() {
        PolicyEvaluationRequest r = base();
        r.setDataClasses(List.of("SUBSTANCE_USE_DISORDER"));
        assertThat(part2.validate(r).getResult()).isEqualTo("FAIL");
    }

    @Test
    void part2_passesWithConsent() {
        PolicyEvaluationRequest r = base();
        r.setDataClasses(List.of("SUBSTANCE_USE_DISORDER"));
        r.setConsentRefs(Map.of("PART_2", "P2-9999"));
        assertThat(part2.validate(r).getResult()).isEqualTo("PASS");
    }

    @Test
    void part2_emergencyExceptionApplies() {
        PolicyEvaluationRequest r = base();
        r.setExchangePurpose(ExchangePurpose.EMERGENCY);
        r.setDataClasses(List.of("SUBSTANCE_USE_DISORDER"));
        assertThat(part2.validate(r).getResult()).isEqualTo("PASS");
    }

    @Test
    void baa_passesWhenAttributesAbsent() {
        assertThat(baa.validate(base()).getResult()).isEqualTo("PASS");
    }

    @Test
    void baa_passesForQhin() {
        PolicyEvaluationRequest r = base();
        r.setPartnerAttributes(Map.of("qhinStatus", "QHIN"));
        assertThat(baa.validate(r).getResult()).isEqualTo("PASS");
    }

    @Test
    void baa_passesForParticipantWithCommonAgreement() {
        PolicyEvaluationRequest r = base();
        r.setPartnerAttributes(Map.of("qhinStatus", "PARTICIPANT", "commonAgreementSigned", true));
        assertThat(baa.validate(r).getResult()).isEqualTo("PASS");
    }

    @Test
    void baa_passesForBaaOnFile() {
        PolicyEvaluationRequest r = base();
        r.setPartnerAttributes(Map.of("qhinStatus", "OTHER", "baaOnFile", true));
        assertThat(baa.validate(r).getResult()).isEqualTo("PASS");
    }

    @Test
    void baa_blocksNonQhinWithoutBaa() {
        PolicyEvaluationRequest r = base();
        r.setPartnerAttributes(Map.of("qhinStatus", "OTHER", "baaOnFile", false));
        assertThat(baa.validate(r).getResult()).isEqualTo("FAIL");
    }

    @Test
    void breakglass_passesWhenNotInvoked() {
        assertThat(breakglass.validate(base()).getResult()).isEqualTo("PASS");
    }

    @Test
    void breakglass_blocksShortJustification() {
        PolicyEvaluationRequest r = base();
        r.setBreakglass(true);
        r.setBreakglassJustification("oops");
        r.setPatientId("PAT-1");
        assertThat(breakglass.validate(r).getResult()).isEqualTo("FAIL");
    }

    @Test
    void breakglass_blocksMissingPatient() {
        PolicyEvaluationRequest r = base();
        r.setBreakglass(true);
        r.setBreakglassJustification("Patient unconscious in ER, needs medical history immediately");
        assertThat(breakglass.validate(r).getResult()).isEqualTo("FAIL");
    }

    @Test
    void breakglass_blocksDisallowedPurpose() {
        PolicyEvaluationRequest r = base();
        r.setExchangePurpose(ExchangePurpose.RESEARCH);
        r.setBreakglass(true);
        r.setBreakglassJustification("Patient unconscious in ER, needs medical history immediately");
        r.setPatientId("PAT-1");
        assertThat(breakglass.validate(r).getResult()).isEqualTo("FAIL");
    }

    @Test
    void breakglass_passesValidInvocation() {
        PolicyEvaluationRequest r = base();
        r.setExchangePurpose(ExchangePurpose.EMERGENCY);
        r.setBreakglass(true);
        r.setBreakglassJustification("Patient unconscious in ER, needs medical history immediately");
        r.setPatientId("PAT-1");
        assertThat(breakglass.validate(r).getResult()).isEqualTo("PASS");
    }

    @Test
    void minimumNecessary_skipsForTreatment() {
        PolicyEvaluationRequest r = base();
        assertThat(minNec.validate(r).getResult()).isEqualTo("PASS");
    }

    @Test
    void minimumNecessary_attachesForPayment() {
        PolicyEvaluationRequest r = base();
        r.setExchangePurpose(ExchangePurpose.PAYMENT);
        assertThat(minNec.validate(r).getResult()).isEqualTo("PASS");
    }
}
