package chit.tefca.ingress.alerting;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Constructs the {@link SnsClient} used by {@link SnsAlertPublisher}.
 *
 * <p>Gated on {@code tefca.alerts.sns-topic-arn} being non-blank so that
 * local development and tests don't need an SNS endpoint or AWS creds. In
 * production the env var is wired by Terraform from the alerting module's
 * topic ARN output.</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(value = "tefca.alerts.sns-topic-arn")
public class SnsClientConfig {

    @Bean(destroyMethod = "close")
    SnsClient snsClient(@Value("${tefca.alerts.aws-region:${AWS_REGION:us-east-1}}") String region) {
        log.info("Creating SnsClient region={}", region);
        return SnsClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(region))
                .build();
    }
}
