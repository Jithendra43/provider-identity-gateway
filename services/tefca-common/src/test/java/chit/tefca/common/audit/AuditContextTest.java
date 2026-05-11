package chit.tefca.common.audit;

import chit.tefca.common.correlation.CorrelationIdHolder;
import chit.tefca.common.enums.TefcaOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditContextTest {

    @BeforeEach
    void setUp() {
        CorrelationIdHolder.set("test-corr-id");
    }

    @AfterEach
    void tearDown() {
        AuditContext.clear();
        CorrelationIdHolder.clear();
    }

    @Test
    void init_shouldCreateBuilderWithCorrelationId() {
        AuditContext.init();
        AuditEvent event = AuditContext.buildAndClear();
        assertThat(event.getCorrelationId()).isEqualTo("test-corr-id");
    }

    @Test
    void current_shouldAutoInitIfNotSet() {
        // No init() call
        AuditEvent.AuditEventBuilder builder = AuditContext.current();
        assertThat(builder).isNotNull();
    }

    @Test
    void buildAndClear_shouldReturnEventAndCleanUp() {
        AuditContext.init();
        AuditContext.current()
                .eventType("TEST_EVENT")
                .operation(TefcaOperation.PATIENT_DISCOVERY)
                .outcome("SUCCESS");

        AuditEvent event = AuditContext.buildAndClear();

        assertThat(event.getEventType()).isEqualTo("TEST_EVENT");
        assertThat(event.getOperation()).isEqualTo(TefcaOperation.PATIENT_DISCOVERY);
        assertThat(event.getOutcome()).isEqualTo("SUCCESS");
    }

    @Test
    void clear_shouldRemoveThreadLocal() {
        AuditContext.init();
        AuditContext.clear();
        // After clear, current() should re-init
        AuditEvent event = AuditContext.buildAndClear();
        assertThat(event).isNotNull();
    }
}
