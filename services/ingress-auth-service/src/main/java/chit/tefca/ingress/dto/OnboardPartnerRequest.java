package chit.tefca.ingress.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Admin API request to onboard a new trading partner. Submitted as JSON to
 * {@code POST /api/v1/admin/partners}. The {@code certificatePem} field
 * carries the partner's leaf mTLS certificate; the service computes the
 * SHA-256 thumbprint server-side rather than trusting a client-supplied one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardPartnerRequest {

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "^[A-Z0-9._\\-]+$",
             message = "orgId must be uppercase alphanumeric with . _ - only")
    private String orgId;

    @NotBlank
    @Size(max = 256)
    private String name;

    @Pattern(regexp = "^(PRODUCTION|STAGING|TEST)$",
             message = "environment must be PRODUCTION, STAGING, or TEST")
    private String environment;

    @Email
    @Size(max = 256)
    private String contactEmail;

    /** When the BAA was signed. Recorded for audit; not gated on. */
    private Instant baaSignedAt;

    /**
     * Partner mTLS leaf certificate, PEM-encoded
     * ({@code -----BEGIN CERTIFICATE-----} ... {@code -----END CERTIFICATE-----}).
     */
    @NotBlank
    @Size(min = 100, max = 16384)
    private String certificatePem;

    /** TEFCA modalities the partner is permitted to call. Recorded in metadata. */
    private List<String> allowedModalities;

    /** OAuth scopes; if non-empty, a partner_oauth_config row is created. */
    private List<String> allowedScopes;

    /** Per-partner rate limit override (defaults to 100 if null). */
    @Min(1)
    private Integer requestsPerMinute;
}
