package chit.tefca.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes audit events to structured log, optional database, and optional evidence store.
 * Uses AuditPersister interface (not JpaRepository directly) so modules without
 * spring-data-jpa on the classpath can still use this publisher.
 */
@Component
public class AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditPublisher.class);

    private final AuditPersister auditPersister;
    private final AuditEvidenceStore evidenceStore;
    private final ObjectMapper objectMapper;

    public AuditPublisher(@Nullable AuditPersister auditPersister,
                          @Nullable AuditEvidenceStore evidenceStore,
                          ObjectMapper objectMapper) {
        this.auditPersister = auditPersister;
        this.evidenceStore = evidenceStore;
        this.objectMapper = objectMapper;
    }

    @Async
    public void publish(AuditEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }

        // Always log
        log.info("AUDIT correlationId={} type={} operation={} requester={}/{} target={}/{} outcome={}",
                event.getCorrelationId(),
                event.getEventType(),
                event.getOperation(),
                event.getRequesterOrgId(),
                event.getRequesterNodeId(),
                event.getTargetOrgId(),
                event.getTargetNodeId(),
                event.getOutcome());

        // Persist to DB if persister available
        if (auditPersister != null) {
            auditPersister.persist(event);
        }

        // Store tamper-evident evidence if store available
        if (evidenceStore != null) {
            try {
                String payload = objectMapper.writeValueAsString(event);
                String previousHash = evidenceStore.getLatestHash();
                evidenceStore.store(event.getEventId(), payload, previousHash);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize audit event for evidence store: {}", event.getEventId(), e);
            }
        }
    }
}
