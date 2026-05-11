package chit.tefca.common.audit;

import chit.tefca.common.correlation.CorrelationIdHolder;
import lombok.Getter;

/**
 * Thread-local audit context for accumulating audit data during a request.
 */
public final class AuditContext {

    private static final ThreadLocal<AuditEvent.AuditEventBuilder> HOLDER = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void init() {
        HOLDER.set(AuditEvent.builder()
                .correlationId(CorrelationIdHolder.get()));
    }

    public static AuditEvent.AuditEventBuilder current() {
        AuditEvent.AuditEventBuilder builder = HOLDER.get();
        if (builder == null) {
            init();
            builder = HOLDER.get();
        }
        return builder;
    }

    public static AuditEvent buildAndClear() {
        try {
            return current().build();
        } finally {
            HOLDER.remove();
        }
    }

    public static void clear() {
        HOLDER.remove();
    }
}
