package chit.tefca.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an organization in the TEFCA directory.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization {
    private String orgId;
    private String name;
    private String oid;
    private String type;
    private boolean active;
}
