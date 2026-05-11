package chit.tefca.ingress.service;

import chit.tefca.common.audit.AuditEvent;
import chit.tefca.common.audit.AuditPublisher;
import chit.tefca.ingress.dto.OnboardPartnerRequest;
import chit.tefca.ingress.dto.OnboardPartnerResponse;
import chit.tefca.ingress.dto.SuspendPartnerRequest;
import chit.tefca.ingress.model.Partner;
import chit.tefca.ingress.model.PartnerCertificate;
import chit.tefca.ingress.model.PartnerOauthConfig;
import chit.tefca.ingress.model.PartnerRateLimit;
import chit.tefca.ingress.repository.PartnerCertificateRepository;
import chit.tefca.ingress.repository.PartnerOauthConfigRepository;
import chit.tefca.ingress.repository.PartnerRateLimitRepository;
import chit.tefca.ingress.repository.PartnerRepository;
import chit.tefca.ingress.security.CertificateOrgMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lifecycle service for trading partners.
 *
 * <p>{@link #onboard(OnboardPartnerRequest)} writes five rows in one
 * transaction: {@code ingress.partners}, {@code ingress.partner_certificates},
 * {@code ingress.partner_oauth_config}, {@code ingress.partner_rate_limits},
 * and an audit event. {@link #suspend(String, SuspendPartnerRequest)} flips
 * the partner row to {@code SUSPENDED}, marks every active certificate
 * inactive, and invalidates the in-process Caffeine cache so the next mTLS
 * request from that partner is rejected within milliseconds rather than
 * waiting for the {@link chit.tefca.ingress.security.PartnerCertificateLoader}
 * refresh interval.</p>
 *
 * <p>Suspend is idempotent — calling it twice on the same partner returns
 * the same response and emits the same audit event but writes no extra rows.</p>
 *
 * <p>Out of scope on purpose:</p>
 * <ul>
 *   <li>BAA storage stays in {@link Partner#getMetadata()} as a JSON blob —
 *       the request just records {@code baaSignedAt} so audit can prove the
 *       BAA pre-existed onboarding.</li>
 *   <li>Uploading the partner's CA into the ALB truststore remains a manual
 *       operator step. This service handles the leaf cert; intermediate /
 *       root trust is configured at the load balancer.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerOnboardingService {

    /** Window-thumbprint key prefix used by audit metadata. */
    private static final String DEFAULT_ENVIRONMENT = "PRODUCTION";

    private final PartnerRepository partnerRepository;
    private final PartnerCertificateRepository certificateRepository;
    private final PartnerOauthConfigRepository oauthConfigRepository;
    private final PartnerRateLimitRepository rateLimitRepository;
    private final CertificateOrgMapper certificateOrgMapper;
    private final AuditPublisher auditPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public OnboardPartnerResponse onboard(OnboardPartnerRequest req) {
        if (partnerRepository.existsByOrgId(req.getOrgId())) {
            throw new PartnerAlreadyExistsException(
                    "A partner with orgId=" + req.getOrgId() + " already exists");
        }

        X509Certificate cert = parseCertificate(req.getCertificatePem());
        String thumbprint = sha256Thumbprint(cert);

        certificateRepository.findByThumbprint(thumbprint).ifPresent(existing -> {
            throw new PartnerAlreadyExistsException(
                    "Certificate thumbprint " + thumbprint
                            + " is already registered to partner " + existing.getPartnerId());
        });

        String partnerId = "PARTNER-" + UUID.randomUUID();

        Partner partner = Partner.builder()
                .partnerId(partnerId)
                .orgId(req.getOrgId())
                .name(req.getName())
                .status("ACTIVE")
                .environment(req.getEnvironment() != null ? req.getEnvironment() : DEFAULT_ENVIRONMENT)
                .metadata(buildMetadataJson(req))
                .build();
        partnerRepository.save(partner);

        PartnerCertificate certificate = PartnerCertificate.builder()
                .certificateId("CERT-" + UUID.randomUUID())
                .partnerId(partnerId)
                .thumbprint(thumbprint)
                .subjectDn(cert.getSubjectX500Principal().getName())
                .issuerDn(cert.getIssuerX500Principal().getName())
                .serialNumber(cert.getSerialNumber().toString(16))
                .notBefore(cert.getNotBefore().toInstant())
                .notAfter(cert.getNotAfter().toInstant())
                .active(true)
                .build();
        certificateRepository.save(certificate);

        if (req.getAllowedScopes() != null && !req.getAllowedScopes().isEmpty()) {
            PartnerOauthConfig oauth = PartnerOauthConfig.builder()
                    .configId("OAUTH-" + UUID.randomUUID())
                    .partnerId(partnerId)
                    .clientId(req.getOrgId())
                    .allowedScopes(req.getAllowedScopes().toArray(String[]::new))
                    .tokenTtlSec(3600)
                    .active(true)
                    .build();
            oauthConfigRepository.save(oauth);
        }

        int rpm = req.getRequestsPerMinute() != null ? req.getRequestsPerMinute() : 100;
        PartnerRateLimit rl = PartnerRateLimit.builder()
                .rateLimitId("RL-" + UUID.randomUUID())
                .partnerId(partnerId)
                .requestsPerMinute(rpm)
                .burstCapacity(Math.max(rpm + (rpm / 2), rpm))
                .active(true)
                .build();
        rateLimitRepository.save(rl);

        Map<String, String> auditMeta = new LinkedHashMap<>();
        auditMeta.put("partnerId", partnerId);
        auditMeta.put("orgId", req.getOrgId());
        auditMeta.put("certThumbprint", thumbprint);
        auditMeta.put("certNotAfter", cert.getNotAfter().toInstant().toString());
        auditMeta.put("environment", partner.getEnvironment());
        if (req.getContactEmail() != null) {
            auditMeta.put("contactEmail", req.getContactEmail());
        }
        if (req.getAllowedModalities() != null) {
            auditMeta.put("allowedModalities", String.join(",", req.getAllowedModalities()));
        }
        auditPublisher.publish(AuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .correlationId(UUID.randomUUID().toString())
                .eventType("PARTNER_ONBOARDED")
                .targetOrgId(req.getOrgId())
                .outcome("SUCCESS")
                .metadata(auditMeta)
                .build());

        log.info("Onboarded partner partnerId={} orgId={} thumbprint={}",
                partnerId, req.getOrgId(), thumbprint);

        return OnboardPartnerResponse.builder()
                .partnerId(partnerId)
                .orgId(req.getOrgId())
                .status("ACTIVE")
                .certificateThumbprint(thumbprint)
                .certificateNotAfter(cert.getNotAfter().toInstant())
                .build();
    }

    @Transactional
    public void suspend(String partnerId, SuspendPartnerRequest req) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new PartnerNotFoundException(
                        "No partner with partnerId=" + partnerId));

        boolean alreadySuspended = "SUSPENDED".equals(partner.getStatus());

        if (!alreadySuspended) {
            partner.setStatus("SUSPENDED");
            partnerRepository.save(partner);
        }

        List<PartnerCertificate> activeCerts =
                certificateRepository.findByPartnerIdAndActiveTrue(partnerId);
        for (PartnerCertificate cert : activeCerts) {
            cert.setActive(false);
            certificateRepository.save(cert);
            certificateOrgMapper.invalidate(cert.getThumbprint());
        }

        Map<String, String> auditMeta = new LinkedHashMap<>();
        auditMeta.put("partnerId", partnerId);
        auditMeta.put("orgId", partner.getOrgId());
        auditMeta.put("certificatesRevoked", String.valueOf(activeCerts.size()));
        auditMeta.put("alreadySuspended", String.valueOf(alreadySuspended));
        if (req != null && req.getReason() != null) {
            auditMeta.put("reason", req.getReason());
        }
        auditPublisher.publish(AuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .correlationId(UUID.randomUUID().toString())
                .eventType("PARTNER_SUSPENDED")
                .targetOrgId(partner.getOrgId())
                .outcome("SUCCESS")
                .metadata(auditMeta)
                .build());

        log.info("Suspended partner partnerId={} orgId={} certificatesRevoked={} alreadySuspended={}",
                partnerId, partner.getOrgId(), activeCerts.size(), alreadySuspended);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private String buildMetadataJson(OnboardPartnerRequest req) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (req.getContactEmail() != null) {
            meta.put("contactEmail", req.getContactEmail());
        }
        if (req.getBaaSignedAt() != null) {
            meta.put("baaOnFile", true);
            meta.put("baaSignedAt", req.getBaaSignedAt().toString());
        }
        if (req.getAllowedModalities() != null && !req.getAllowedModalities().isEmpty()) {
            meta.put("allowedModalities", req.getAllowedModalities());
        }
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise partner metadata for orgId={}: {}",
                    req.getOrgId(), e.getMessage());
            return "{}";
        }
    }

    private static X509Certificate parseCertificate(String pem) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
        } catch (CertificateException e) {
            throw new InvalidCertificateException(
                    "Could not parse PEM as X.509: " + e.getMessage(), e);
        }
    }

    private static String sha256Thumbprint(X509Certificate cert) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            return HexFormat.of().formatHex(digest).toUpperCase();
        } catch (Exception e) {
            throw new InvalidCertificateException(
                    "Could not compute SHA-256 thumbprint: " + e.getMessage(), e);
        }
    }

    // ── domain exceptions ───────────────────────────────────────────────

    public static class PartnerAlreadyExistsException extends RuntimeException {
        public PartnerAlreadyExistsException(String msg) { super(msg); }
    }

    public static class PartnerNotFoundException extends RuntimeException {
        public PartnerNotFoundException(String msg) { super(msg); }
    }

    public static class InvalidCertificateException extends RuntimeException {
        public InvalidCertificateException(String msg, Throwable cause) { super(msg, cause); }
    }
}
