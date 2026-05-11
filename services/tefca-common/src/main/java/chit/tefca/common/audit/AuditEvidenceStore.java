package chit.tefca.common.audit;

/**
 * Interface for storing tamper-evident audit evidence.
 * In production, use S3-backed implementation; in dev/test, use filesystem.
 */
public interface AuditEvidenceStore {

    /**
     * Store audit evidence with a SHA-256 hash chain.
     * @param eventId unique event identifier
     * @param payload serialized audit event
     * @param previousHash SHA-256 hash of the previous evidence record (null for first)
     * @return the SHA-256 hash of this evidence record
     */
    String store(String eventId, String payload, String previousHash);

    /**
     * Retrieve stored evidence by event ID.
     * @param eventId unique event identifier
     * @return the stored evidence payload, or null if not found
     */
    String retrieve(String eventId);

    /**
     * Get the hash of the most recent evidence record.
     * @return the SHA-256 hash, or null if no evidence stored
     */
    String getLatestHash();
}
