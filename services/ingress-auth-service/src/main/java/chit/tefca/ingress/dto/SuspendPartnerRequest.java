package chit.tefca.ingress.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin API request to suspend (offboard) a partner. The reason is
 * optional but strongly recommended; it is recorded verbatim in the audit
 * event metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuspendPartnerRequest {

    @Size(max = 512)
    private String reason;
}
