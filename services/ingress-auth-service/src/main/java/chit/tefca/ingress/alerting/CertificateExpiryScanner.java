package chit.tefca.ingress.alerting;

import chit.tefca.ingress.model.PartnerCertificate;
import chit.tefca.ingress.repository.PartnerCertificateRepository;
import chit.tefca.ingress.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Daily job that fans out an SNS alert for every active partner certificate
 * expiring within the configured window (default: 30 days).
 *
 * <p>Runs at 13:00 UTC = 09:00 ET so the message lands in operators'
 * inboxes at the start of the US business day. The cron is overridable via
 * {@code tefca.alerts.cert-expiry-cron}.</p>
 *
 * <p>Dedupes within a single run by {@code (thumbprint, dayOfRun)} so that
 * a job retry inside the same JVM doesn't double-send. Cross-run dedupe
 * (e.g. across restarts) is intentionally not implemented — operators much
 * prefer two emails to none.</p>
 *
 * <p>Disabled by default; activates when {@code tefca.alerts.sns-topic-arn}
 * is set. Without that property, {@link SnsAlertPublisher} won't even be
 * constructed and this scanner would have nothing to publish to.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "tefca.alerts.sns-topic-arn")
public class CertificateExpiryScanner {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final PartnerCertificateRepository certificateRepository;
    private final PartnerRepository partnerRepository;
    private final SnsAlertPublisher alertPublisher;

    @Value("${tefca.alerts.cert-expiry-window-days:30}")
    private int windowDays;

    private final Set<String> sentToday = new HashSet<>();
    private LocalDate sentDay;

    /** 13:00 UTC daily. */
    @Scheduled(cron = "${tefca.alerts.cert-expiry-cron:0 0 13 * * *}", zone = "UTC")
    public void scan() {
        Instant cutoff = Instant.now().plus(Duration.ofDays(windowDays));
        List<PartnerCertificate> expiring =
                certificateRepository.findByActiveTrueAndNotAfterBefore(cutoff);

        log.info("Cert expiry scan window={}d cutoff={} found={}",
                windowDays, cutoff, expiring.size());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        synchronized (this) {
            if (!today.equals(sentDay)) {
                sentToday.clear();
                sentDay = today;
            }
        }

        int sent = 0, skipped = 0;
        for (PartnerCertificate cert : expiring) {
            String dedupeKey = cert.getThumbprint() + "|" + DATE_FMT.format(today.atStartOfDay(ZoneOffset.UTC).toInstant());
            synchronized (this) {
                if (!sentToday.add(dedupeKey)) {
                    skipped++;
                    continue;
                }
            }

            String orgId = partnerRepository.findById(cert.getPartnerId())
                    .map(p -> p.getOrgId())
                    .orElse("(unknown-partner)");

            long daysLeft = Duration.between(Instant.now(), cert.getNotAfter()).toDays();
            String subject = "TEFCA cert expiring in " + daysLeft + "d: " + orgId;
            String body = """
                    Trading partner mTLS certificate expiring soon.

                    Partner orgId : %s
                    Partner ID    : %s
                    Subject DN    : %s
                    Thumbprint    : %s
                    Not After     : %s  (in %d days)

                    Action: contact the partner to obtain a renewed leaf certificate
                    and onboard it via POST /api/v1/admin/partners (a new cert row
                    will be added; the old cert remains active until expiry).
                    """.formatted(
                            orgId,
                            cert.getPartnerId(),
                            cert.getSubjectDn(),
                            cert.getThumbprint(),
                            cert.getNotAfter(),
                            daysLeft);

            alertPublisher.publish(subject, body);
            sent++;
        }

        log.info("Cert expiry scan complete sent={} skippedDuplicate={} totalExpiring={}",
                sent, skipped, expiring.size());
    }
}
