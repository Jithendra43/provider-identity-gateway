package chit.tefca.ingress.repository;

import chit.tefca.ingress.model.PartnerCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerCertificateRepository extends JpaRepository<PartnerCertificate, String> {

    Optional<PartnerCertificate> findByThumbprintAndActiveTrue(String thumbprint);

    Optional<PartnerCertificate> findByThumbprint(String thumbprint);

    List<PartnerCertificate> findByPartnerIdAndActiveTrue(String partnerId);

    int countByPartnerIdAndActiveTrue(String partnerId);

    /**
     * All currently-active certificates whose validity ends before {@code cutoff}.
     * Used by the daily expiry scanner — pass {@code now() + 30d} to find
     * certs that expire in the next 30 days.
     */
    List<PartnerCertificate> findByActiveTrueAndNotAfterBefore(Instant cutoff);
}
