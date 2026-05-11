package chit.tefca.policy.dto;

import chit.tefca.common.enums.ExchangePurpose;
import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyEvaluationRequest {

    @NotBlank
    private String correlationId;

    @NotNull
    private TefcaOperation operation;

    @NotNull
    private ExchangePurpose exchangePurpose;

    @NotNull
    private Modality modality;

    @NotBlank
    private String requesterOrgId;

    @NotBlank
    private String requesterNodeId;

    private String targetOrgId;

    private String targetNodeId;

    private String patientId;

    private List<String> requesterRoles;

    /**
     * HIPAA / 42 CFR Part 2 sensitive-data classifiers carried in the request payload.
     * Recognized values: PSYCHOTHERAPY_NOTES, SUBSTANCE_USE_DISORDER, HIV, MENTAL_HEALTH,
     * REPRODUCTIVE_HEALTH, GENETIC, SSN, MARKETING, SALE_OF_PHI.
     */
    private List<String> dataClasses;

    /**
     * Breakglass invocation per TEFCA Common Agreement §SOP-Breakglass.
     * Requires non-blank justification and triggers elevated audit obligations.
     */
    private boolean breakglass;

    private String breakglassJustification;

    /**
     * Map of regulatory consent references the requester asserts (e.g. INDIVIDUAL_AUTH=patient signed authorization,
     * PART_2=42 CFR Part 2 consent, TPO=treatment/payment/operations under §164.506).
     * Values are opaque consent-record identifiers from the requester.
     */
    private Map<String, String> consentRefs;

    /**
     * Trust attributes about the target/recipient organization, typically populated from
     * directory cache: baaOnFile (Boolean), qhinStatus (String: QHIN|PARTICIPANT|SUBPARTICIPANT),
     * commonAgreementSigned (Boolean), active (Boolean).
     */
    private Map<String, Object> partnerAttributes;
}
