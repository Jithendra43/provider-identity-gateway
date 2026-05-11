package chit.tefca.ingress.repository;

import chit.tefca.ingress.model.PartnerRateLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartnerRateLimitRepository extends JpaRepository<PartnerRateLimit, String> {

    Optional<PartnerRateLimit> findByPartnerIdAndActiveTrue(String partnerId);
}
