package chit.tefca.ingress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Contextual information about the authenticated partner, resolved from JWT + partner DB.
 * Carried through the request lifecycle for per-partner policy enforcement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerContext {

    private String partnerId;
    private String orgId;
    private String nodeId;
    private String partnerName;
    private String environment;
    private int requestsPerMinute;
    private int burstCapacity;
    private List<String> roles;
    private List<String> allowedScopes;
    private String certificateThumbprint;

    /**
     * Whether this partner was resolved from the database (as opposed to JWT-only).
     */
    private boolean dbResolved;
}
