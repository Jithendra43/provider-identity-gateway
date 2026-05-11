package chit.tefca.policy.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Obligation attached to a PERMIT decision — describes a required
 * downstream action (e.g. audit logging, consent verification).
 * Stored as part of the policy rule expression; not a JPA entity
 * on its own but embedded within rule evaluation results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Obligation {

    private String obligationId;
    private String description;
    private String category;
    private boolean mandatory;
}
