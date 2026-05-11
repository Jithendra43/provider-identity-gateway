package chit.tefca.common.dto;

import chit.tefca.common.enums.ExchangePurpose;
import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical TEFCA request envelope used across all gateway services.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TefcaRequest {

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

    private String patientIdSystem;

    private String documentId;

    private Map<String, Object> payload;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private String idempotencyKey;

    private Map<String, String> metadata;
}
