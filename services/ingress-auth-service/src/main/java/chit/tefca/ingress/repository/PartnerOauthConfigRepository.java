package chit.tefca.ingress.repository;

import chit.tefca.ingress.model.PartnerOauthConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartnerOauthConfigRepository extends JpaRepository<PartnerOauthConfig, String> {

    Optional<PartnerOauthConfig> findByPartnerIdAndActiveTrue(String partnerId);

    Optional<PartnerOauthConfig> findByClientIdAndActiveTrue(String clientId);
}
