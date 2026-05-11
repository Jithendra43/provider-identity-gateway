package chit.tefca.ingress.alerting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

/**
 * Thin SNS wrapper used by alerting jobs.
 *
 * <p>Bean is only created when {@code tefca.alerts.sns-topic-arn} is set —
 * see {@link SnsClientConfig}. Callers should accept this bean as
 * {@code @Autowired(required = false)} (or {@link org.springframework.beans.factory.ObjectProvider})
 * and silently skip publishing when absent.</p>
 *
 * <p>Failures are logged but never thrown; alerting must not knock over the
 * partner trust loader or any other scheduled job sharing a TaskScheduler
 * thread pool.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "tefca.alerts.sns-topic-arn")
public class SnsAlertPublisher {

    private final SnsClient snsClient;

    @Value("${tefca.alerts.sns-topic-arn}")
    private String topicArn;

    public void publish(String subject, String message) {
        try {
            PublishResponse resp = snsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject(truncateSubject(subject))
                    .message(message)
                    .build());
            log.info("SNS publish ok topicArn={} messageId={} subject={}",
                    topicArn, resp.messageId(), subject);
        } catch (SnsException e) {
            // Don't rethrow — alerting must never break the caller (cron job).
            log.error("SNS publish failed topicArn={} subject={}: {}",
                    topicArn, subject, e.awsErrorDetails().errorMessage(), e);
        }
    }

    /** SNS caps subject at 100 characters. */
    private static String truncateSubject(String subject) {
        if (subject == null) return "TEFCA Alert";
        return subject.length() <= 100 ? subject : subject.substring(0, 100);
    }
}
