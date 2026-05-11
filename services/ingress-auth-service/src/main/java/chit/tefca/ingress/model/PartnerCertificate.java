package chit.tefca.ingress.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "partner_certificates", schema = "ingress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartnerCertificate {

    @Id
    @Column(name = "certificate_id", length = 64)
    private String certificateId;

    @Column(name = "partner_id", nullable = false, length = 64)
    private String partnerId;

    @Column(name = "thumbprint", nullable = false, unique = true, length = 128)
    private String thumbprint;

    @Column(name = "subject_dn", nullable = false, length = 512)
    private String subjectDn;

    @Column(name = "issuer_dn", length = 512)
    private String issuerDn;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "not_before", nullable = false)
    private Instant notBefore;

    @Column(name = "not_after", nullable = false)
    private Instant notAfter;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
