package chit.tefca.ingress.repository;

import chit.tefca.ingress.model.DirectoryEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DirectoryEndpointRepository extends JpaRepository<DirectoryEndpoint, String> {

    List<DirectoryEndpoint> findByPartnerId(String partnerId);

    List<DirectoryEndpoint> findByPartnerIdAndActiveTrue(String partnerId);
}
