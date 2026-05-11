package chit.tefca.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import chit.tefca.common.enums.TefcaOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditPublisherTest {

    @Mock
    private AuditPersister auditPersister;

    @Mock
    private AuditEvidenceStore evidenceStore;

    private ObjectMapper objectMapper;
    private AuditPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        publisher = new AuditPublisher(auditPersister, evidenceStore, objectMapper);
    }

    @Test
    void publish_shouldPersistViaPersister() {
        AuditEvent event = AuditEvent.builder()
                .eventId("evt-1")
                .correlationId("corr-1")
                .eventType("INGRESS_PATIENT_DISCOVERY")
                .operation(TefcaOperation.PATIENT_DISCOVERY)
                .requesterOrgId("org-1")
                .targetOrgId("org-2")
                .outcome("SUCCESS")
                .build();

        publisher.publish(event);

        verify(auditPersister).persist(event);
    }

    @Test
    void publish_shouldStoreEvidence() {
        when(evidenceStore.getLatestHash()).thenReturn("prev-hash");
        when(evidenceStore.store(anyString(), anyString(), anyString())).thenReturn("new-hash");

        AuditEvent event = AuditEvent.builder()
                .eventId("evt-2")
                .correlationId("corr-2")
                .eventType("INGRESS_DOCUMENT_QUERY")
                .outcome("DENIED")
                .build();

        publisher.publish(event);

        verify(evidenceStore).store(eq("evt-2"), anyString(), eq("prev-hash"));
    }

    @Test
    void publish_shouldGenerateEventIdIfMissing() {
        AuditEvent event = AuditEvent.builder()
                .correlationId("corr-3")
                .eventType("TEST")
                .outcome("SUCCESS")
                .build();

        publisher.publish(event);

        assertThat(event.getEventId()).isNotNull();
        verify(auditPersister).persist(event);
    }

    @Test
    void publish_withNullDependencies_shouldOnlyLog() {
        AuditPublisher logOnlyPublisher = new AuditPublisher(null, null, objectMapper);

        AuditEvent event = AuditEvent.builder()
                .eventId("evt-4")
                .correlationId("corr-4")
                .eventType("TEST")
                .outcome("SUCCESS")
                .build();

        // Should not throw
        logOnlyPublisher.publish(event);
    }

    @Test
    void publish_shouldDelegateMetadataContainingEvent() {
        AuditEvent event = AuditEvent.builder()
                .eventId("evt-5")
                .correlationId("corr-5")
                .eventType("INGRESS_MESSAGE_DELIVERY")
                .operation(TefcaOperation.MESSAGE_DELIVERY)
                .requesterOrgId("org-1")
                .requesterNodeId("node-1")
                .targetOrgId("org-2")
                .outcome("SUCCESS")
                .policyDecision("PERMIT")
                .metadata(Map.of("key", "value"))
                .build();

        publisher.publish(event);

        verify(auditPersister).persist(event);
        assertThat(event.getMetadata()).containsEntry("key", "value");
    }
}
