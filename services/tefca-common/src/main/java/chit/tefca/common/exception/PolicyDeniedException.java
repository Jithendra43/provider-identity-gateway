package chit.tefca.common.exception;

/**
 * Thrown when a TEFCA policy evaluation denies the request.
 */
public class PolicyDeniedException extends TefcaException {

    private final String reason;

    public PolicyDeniedException(String reason) {
        super("POLICY_DENIED", "Request denied by policy: " + reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
