package chit.tefca.common.audit;

/**
 * Strategy interface for persisting audit events.
 * Decoupled from JPA so modules without spring-data-jpa can still use AuditPublisher.
 */
public interface AuditPersister {

    void persist(AuditEvent event);
}
