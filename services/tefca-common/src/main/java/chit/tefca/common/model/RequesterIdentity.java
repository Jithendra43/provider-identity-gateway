package chit.tefca.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Authenticated requester identity extracted from JWT/mTLS.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequesterIdentity {
    private String subject;
    private String orgId;
    private String nodeId;
    private String issuer;
    private List<String> roles;
    private List<String> scopes;
    private String certificateThumbprint;
}
