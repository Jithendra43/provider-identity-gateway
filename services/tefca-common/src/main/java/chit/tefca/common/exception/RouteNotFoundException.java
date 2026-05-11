package chit.tefca.common.exception;

/**
 * Thrown when no viable route can be resolved for a TEFCA request.
 */
public class RouteNotFoundException extends TefcaException {

    public RouteNotFoundException(String message) {
        super("ROUTE_NOT_FOUND", message);
    }
}
