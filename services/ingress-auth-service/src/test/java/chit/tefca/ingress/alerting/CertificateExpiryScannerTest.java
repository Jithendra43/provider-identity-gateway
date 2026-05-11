package chit.tefca.ingress.alerting;

import chit.tefca.ingress.model.Partner;
import chit.tefca.ingress.model.PartnerCertificate;
import chit.tefca.ingress.repository.PartnerCertificateRepository;
import chit.tefca.ingress.repository.PartnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateExpiryScannerTest {

    @Mock private PartnerCertificateRepository certificateRepository;
    @Mock private PartnerRepository partnerRepository;
    @Mock private SnsAlertPublisher alertPublisher;

    private CertificateExpiryScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new CertificateExpiryScanner(
                certificateRepository, partnerRepository, alertPublisher);
        ReflectionTestUtils.setField(scanner, "windowDays", 30);
    }

    @Test
    void scan_publishesOnePerExpiringCertAndDedupesOnSecondRun() {
        PartnerCertificate c1 = PartnerCertificate.builder()
                .certificateId("CERT-1").partnerId("PARTNER-1")
                .thumbprint("THUMB-A").subjectDn("CN=A")
                .notAfter(Instant.now().plus(5, ChronoUnit.DAYS))
                .active(true).build();
        PartnerCertificate c2 = PartnerCertificate.builder()
                .certificateId("CERT-2").partnerId("PARTNER-2")
                .thumbprint("THUMB-B").subjectDn("CN=B")
                .notAfter(Instant.now().plus(20, ChronoUnit.DAYS))
                .active(true).build();
        when(certificateRepository.findByActiveTrueAndNotAfterBefore(any()))
                .thenReturn(List.of(c1, c2));
        when(partnerRepository.findById("PARTNER-1")).thenReturn(Optional.of(
                Partner.builder().partnerId("PARTNER-1").orgId("ORG-A").build()));
        when(partnerRepository.findById("PARTNER-2")).thenReturn(Optional.of(
                Partner.builder().partnerId("PARTNER-2").orgId("ORG-B").build()));

        scanner.scan();

        verify(alertPublisher).publish(contains("ORG-A"), contains("THUMB-A"));
        verify(alertPublisher).publish(contains("ORG-B"), contains("THUMB-B"));
        verify(alertPublisher, times(2)).publish(any(), any());

        // Second invocation same day → all duplicates, no further publishes.
        scanner.scan();
        verify(alertPublisher, times(2)).publish(any(), any());
    }

    @Test
    void scan_publishesNothingWhenNoExpiringCerts() {
        when(certificateRepository.findByActiveTrueAndNotAfterBefore(any()))
                .thenReturn(List.of());

        scanner.scan();

        verify(alertPublisher, never()).publish(any(), any());
    }

    @Test
    void scan_handlesUnknownPartnerGracefully() {
        PartnerCertificate orphan = PartnerCertificate.builder()
                .certificateId("CERT-X").partnerId("PARTNER-MISSING")
                .thumbprint("THUMB-X").subjectDn("CN=X")
                .notAfter(Instant.now().plus(1, ChronoUnit.DAYS))
                .active(true).build();
        when(certificateRepository.findByActiveTrueAndNotAfterBefore(any()))
                .thenReturn(List.of(orphan));
        when(partnerRepository.findById("PARTNER-MISSING")).thenReturn(Optional.empty());

        scanner.scan();

        verify(alertPublisher).publish(contains("(unknown-partner)"), contains("PARTNER-MISSING"));
    }
}
