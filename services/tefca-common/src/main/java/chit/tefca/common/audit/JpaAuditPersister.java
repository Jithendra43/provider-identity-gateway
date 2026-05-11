package chit.tefca.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA-backed AuditPersister that delegates to AuditEventRepository.
 * Only instantiated when spring-data-jpa is on the classpath and the repository bean exists.
 */
public class JpaAuditPersister implements AuditPersister {

    private static final Logger log = LoggerFactory.getLogger(JpaAuditPersister.class);

    private final AuditEventRepository auditEventRepository;

    public JpaAuditPersister(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    public void persist(AuditEvent event) {
        try {
            AuditEventEntity entity = AuditEventEntity.fromAuditEvent(event);
            auditEventRepository.save(entity);
            log.debug("Persisted audit event: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to persist audit event: {}", event.getEventId(), e);
        }
    }
}
