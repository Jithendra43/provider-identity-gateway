package chit.tefca.common.audit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA-specific audit configuration. Only loaded when spring-data-jpa is on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(JpaRepository.class)
public class AuditJpaConfig {

    @Bean
    @ConditionalOnMissingBean(AuditPersister.class)
    @ConditionalOnBean(AuditEventRepository.class)
    public AuditPersister jpaAuditPersister(AuditEventRepository repository) {
        return new JpaAuditPersister(repository);
    }

    @Bean
    @ConditionalOnBean(AuditEventRepository.class)
    @ConditionalOnMissingBean(AuditQueryController.class)
    public AuditQueryController auditQueryController(AuditEventRepository repository) {
        return new AuditQueryController(repository);
    }
}
