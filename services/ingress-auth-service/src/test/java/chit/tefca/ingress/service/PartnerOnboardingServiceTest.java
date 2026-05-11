package chit.tefca.ingress.service;

import chit.tefca.common.audit.AuditEvent;
import chit.tefca.common.audit.AuditPublisher;
import chit.tefca.ingress.dto.OnboardPartnerRequest;
import chit.tefca.ingress.dto.OnboardPartnerResponse;
import chit.tefca.ingress.dto.SuspendPartnerRequest;
import chit.tefca.ingress.model.Partner;
import chit.tefca.ingress.model.PartnerCertificate;
import chit.tefca.ingress.repository.PartnerCertificateRepository;
import chit.tefca.ingress.repository.PartnerOauthConfigRepository;
import chit.tefca.ingress.repository.PartnerRateLimitRepository;
import chit.tefca.ingress.repository.PartnerRepository;
import chit.tefca.ingress.security.CertificateOrgMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerOnboardingServiceTest {

    @Mock private PartnerRepository partnerRepository;
    @Mock private PartnerCertificateRepository certificateRepository;
    @Mock private PartnerOauthConfigRepository oauthConfigRepository;
    @Mock private PartnerRateLimitRepository rateLimitRepository;
    @Mock private CertificateOrgMapper certificateOrgMapper;
    @Mock private AuditPublisher auditPublisher;

    private PartnerOnboardingService service;

    @BeforeEach
    void setUp() {
        service = new PartnerOnboardingService(
                partnerRepository, certificateRepository, oauthConfigRepository,
                rateLimitRepository, certificateOrgMapper, auditPublisher,
                new ObjectMapper());
    }

    @Test
    void onboard_persistsAllFiveRowsAndReturnsThumbprint() throws Exception {
        when(partnerRepository.existsByOrgId("ORG-NEW")).thenReturn(false);
        when(certificateRepository.findByThumbprint(any())).thenReturn(Optional.empty());

        String pem = generateSelfSignedPem();
        OnboardPartnerRequest req = OnboardPartnerRequest.builder()
                .orgId("ORG-NEW")
                .name("New Partner Inc.")
                .environment("PRODUCTION")
                .contactEmail("ops@new.example")
                .baaSignedAt(Instant.now())
                .certificatePem(pem)
                .allowedModalities(List.of("PATIENT_DISCOVERY", "DOCUMENT_QUERY"))
                .allowedScopes(List.of("system/Patient.read"))
                .requestsPerMinute(150)
                .build();

        OnboardPartnerResponse resp = service.onboard(req);

        assertThat(resp.getPartnerId()).startsWith("PARTNER-");
        assertThat(resp.getOrgId()).isEqualTo("ORG-NEW");
        assertThat(resp.getStatus()).isEqualTo("ACTIVE");
        assertThat(resp.getCertificateThumbprint()).hasSize(64);
        assertThat(resp.getCertificateNotAfter()).isAfter(Instant.now());

        verify(partnerRepository).save(any(Partner.class));
        verify(certificateRepository).save(any(PartnerCertificate.class));
        verify(oauthConfigRepository).save(any());
        verify(rateLimitRepository).save(any());

        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(auditCap.capture());
        AuditEvent ev = auditCap.getValue();
        assertThat(ev.getEventType()).isEqualTo("PARTNER_ONBOARDED");
        assertThat(ev.getOutcome()).isEqualTo("SUCCESS");
        assertThat(ev.getMetadata()).containsEntry("orgId", "ORG-NEW");
    }

    @Test
    void onboard_rejectsDuplicateOrgId() {
        when(partnerRepository.existsByOrgId("ORG-DUPE")).thenReturn(true);

        OnboardPartnerRequest req = OnboardPartnerRequest.builder()
                .orgId("ORG-DUPE").name("x").certificatePem("ignored").build();

        assertThatThrownBy(() -> service.onboard(req))
                .isInstanceOf(PartnerOnboardingService.PartnerAlreadyExistsException.class);

        verify(partnerRepository, never()).save(any());
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void onboard_rejectsBadPem() {
        when(partnerRepository.existsByOrgId("ORG-X")).thenReturn(false);

        OnboardPartnerRequest req = OnboardPartnerRequest.builder()
                .orgId("ORG-X").name("x")
                .certificatePem("not a real pem").build();

        assertThatThrownBy(() -> service.onboard(req))
                .isInstanceOf(PartnerOnboardingService.InvalidCertificateException.class);
    }

    @Test
    void suspend_marksPartnerSuspendedAndRevokesCertsAndInvalidatesCache() {
        Partner p = Partner.builder()
                .partnerId("PARTNER-1").orgId("ORG-1").name("p").status("ACTIVE")
                .environment("PRODUCTION").build();
        when(partnerRepository.findById("PARTNER-1")).thenReturn(Optional.of(p));
        PartnerCertificate c1 = PartnerCertificate.builder()
                .certificateId("CERT-1").partnerId("PARTNER-1")
                .thumbprint("THUMB-1").subjectDn("CN=A")
                .notBefore(Instant.now()).notAfter(Instant.now().plusSeconds(86400))
                .active(true).build();
        when(certificateRepository.findByPartnerIdAndActiveTrue("PARTNER-1"))
                .thenReturn(List.of(c1));

        service.suspend("PARTNER-1", new SuspendPartnerRequest("policy violation"));

        assertThat(p.getStatus()).isEqualTo("SUSPENDED");
        assertThat(c1.isActive()).isFalse();
        verify(certificateRepository).save(c1);
        verify(certificateOrgMapper).invalidate("THUMB-1");

        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditPublisher).publish(auditCap.capture());
        assertThat(auditCap.getValue().getEventType()).isEqualTo("PARTNER_SUSPENDED");
        assertThat(auditCap.getValue().getMetadata()).containsEntry("reason", "policy violation");
    }

    @Test
    void suspend_isIdempotent() {
        Partner p = Partner.builder()
                .partnerId("PARTNER-2").orgId("ORG-2").name("p").status("SUSPENDED")
                .environment("PRODUCTION").build();
        when(partnerRepository.findById("PARTNER-2")).thenReturn(Optional.of(p));
        when(certificateRepository.findByPartnerIdAndActiveTrue("PARTNER-2"))
                .thenReturn(List.of());

        service.suspend("PARTNER-2", null);

        // status was already SUSPENDED -> no extra save
        verify(partnerRepository, never()).save(any());
        // audit event still emitted (idempotent observability)
        verify(auditPublisher).publish(any());
    }

    @Test
    void suspend_throwsWhenPartnerMissing() {
        when(partnerRepository.findById("MISSING")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.suspend("MISSING", null))
                .isInstanceOf(PartnerOnboardingService.PartnerNotFoundException.class);
    }

    // ── helpers ────────────────────────────────────────────────────────

    /**
     * Generates a throwaway self-signed RSA certificate using BouncyCastle
     * (test scope only). Never used in production code paths — production
     * uses BC-FIPS via the gateway-app's JCA provider config.
     */
    private static String generateSelfSignedPem() throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        Date from = new Date();
        Date to = Date.from(Instant.now().plus(365, ChronoUnit.DAYS));
        org.bouncycastle.asn1.x500.X500Name owner =
                new org.bouncycastle.asn1.x500.X500Name("CN=test-partner,O=TEST");
        org.bouncycastle.cert.X509v3CertificateBuilder builder =
                new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                        owner,
                        BigInteger.valueOf(System.currentTimeMillis()),
                        from, to,
                        owner,
                        kp.getPublic());
        org.bouncycastle.operator.ContentSigner signer =
                new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
                        .build(kp.getPrivate());
        org.bouncycastle.cert.X509CertificateHolder holder = builder.build(signer);
        X509Certificate cert =
                new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(holder);
        byte[] der = cert.getEncoded();
        String b64 = java.util.Base64.getMimeEncoder(64,
                new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN CERTIFICATE-----\n" + b64 + "\n-----END CERTIFICATE-----\n";
    }

    @SuppressWarnings("unused")
    private X509Certificate ignored; // silences unused-import warning if any
}
