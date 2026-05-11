package chit.tefca.routing.dto;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteRequest {

    @NotBlank
    private String correlationId;

    @NotNull
    private TefcaOperation operation;

    @NotNull
    private Modality modality;

    @NotBlank
    private String targetOrgId;

    private String targetNodeId;

    @NotBlank
    private String requesterOrgId;

    private String idempotencyKey;

    private Map<String, Object> payload;

    // ── Prior Authorization (PA) extensions ────────────────────────────────
    /** Raw request body forwarded verbatim downstream (CDS Hooks JSON, FHIR Bundle, etc.). */
    private String rawBody;

    /** HTTP method to use when forwarding (defaults to POST when null). */
    private String httpMethod;

    /** Bearer token to inject as Authorization header on the downstream call (PA internal JWT). */
    private String downstreamAuthorization;

    /** Extra headers to forward to the downstream endpoint (e.g. correlation ids, PA hooks metadata). */
    private Map<String, String> additionalHeaders;
}
