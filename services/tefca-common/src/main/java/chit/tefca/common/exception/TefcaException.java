package chit.tefca.common.exception;

/**
 * Base exception for all TEFCA Gateway errors.
 */
public class TefcaException extends RuntimeException {

    private final String errorCode;

    public TefcaException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TefcaException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
