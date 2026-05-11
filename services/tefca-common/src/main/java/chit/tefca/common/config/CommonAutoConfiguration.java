package chit.tefca.common.config;

import chit.tefca.common.audit.AuditConfig;
import chit.tefca.common.audit.AuditPublisher;
import chit.tefca.common.correlation.CorrelationIdFilter;
import chit.tefca.common.exception.GlobalExceptionHandler;
import chit.tefca.common.security.JwtClaimsExtractor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
        JacksonConfig.class,
        CorrelationIdFilter.class,
        GlobalExceptionHandler.class,
        AuditConfig.class,
        AuditPublisher.class,
        JwtClaimsExtractor.class
})
public class CommonAutoConfiguration {
}
