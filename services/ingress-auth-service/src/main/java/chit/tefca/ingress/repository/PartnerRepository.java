package chit.tefca.ingress.repository;

import chit.tefca.ingress.model.Partner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerRepository extends JpaRepository<Partner, String> {

    Optional<Partner> findByOrgId(String orgId);

    List<Partner> findByStatus(String status);

    boolean existsByOrgId(String orgId);
}
