package chit.tefca.common.enums;

/**
 * TEFCA exchange modalities — the transport/protocol method.
 */
public enum Modality {
    XCPD,       // Cross-Community Patient Discovery
    XCA_QUERY,  // Cross-Community Access Query
    XCA_RETRIEVE, // Cross-Community Access Retrieve
    XDR,        // Cross-Enterprise Document Reliable Interchange
    FHIR,       // FHIR REST
    DIRECT,      // Direct Messaging

    // ── Prior Authorization (PA) modalities ────────────────────────────────
    // CRD — CDS Hooks 2.0 (Da Vinci Coverage Requirements Discovery)
    PA_ORDER_SIGN,
    PA_ORDER_SELECT,
    PA_APPOINTMENT_BOOK,
    PA_ORDER_DISPATCH,
    PA_ENCOUNTER_START,
    PA_ENCOUNTER_DISCHARGE,
    // DTR — FHIR REST (Da Vinci Documentation Templates and Rules)
    PA_DTR_QUESTIONNAIRE_PACKAGE,
    PA_DTR_QUESTIONNAIRE_READ,
    PA_DTR_LIBRARY_READ,
    PA_DTR_RESPONSE_SUBMIT,
    PA_DTR_RESPONSE_READ,
    // PAS — FHIR REST (Da Vinci Prior Authorization Support)
    PA_CLAIM_SUBMIT,
    PA_CLAIM_INQUIRE,
    PA_CLAIM_RESPONSE_READ;

    /** True when this modality is part of the Prior Authorization workflow. */
    public boolean isPa() {
        return name().startsWith("PA_");
    }
}
