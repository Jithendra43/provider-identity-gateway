package chit.tefca.common.exception;

/**
 * Thrown when a directory lookup fails.
 */
public class DirectoryLookupException extends TefcaException {

    public DirectoryLookupException(String message) {
        super("DIRECTORY_LOOKUP_FAILED", message);
    }

    public DirectoryLookupException(String message, Throwable cause) {
        super("DIRECTORY_LOOKUP_FAILED", message, cause);
    }
}
