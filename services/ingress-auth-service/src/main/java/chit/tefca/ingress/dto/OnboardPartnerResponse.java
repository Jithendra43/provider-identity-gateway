package chit.tefca.ingress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Successful response from {@code POST /api/v1/admin/partners}. Returns the
 * generated server-side identifiers and the parsed cert validity so the
 * operator can confirm the right cert was registered.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardPartnerResponse {

    private String partnerId;
    private String orgId;
    private String status;
    private String certificateThumbprint;
    private Instant certificateNotAfter;
}
