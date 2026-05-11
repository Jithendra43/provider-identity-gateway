package chit.tefca.common.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class AuditConfig {

    @Bean
    @ConditionalOnMissingBean(AuditEvidenceStore.class)
    @ConditionalOnProperty(name = "tefca.audit.evidence.enabled", havingValue = "true", matchIfMissing = false)
    public AuditEvidenceStore fileAuditEvidenceStore(
            @Value("${tefca.audit.evidence.path:./audit-evidence}") String evidencePath) {
        return new FileAuditEvidenceStore(Path.of(evidencePath));
    }
}
