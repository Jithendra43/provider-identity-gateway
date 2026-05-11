package chit.tefca.directory.repository;

import chit.tefca.directory.model.DirectoryOrganization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<DirectoryOrganization, String> {

    Optional<DirectoryOrganization> findByOid(String oid);

    List<DirectoryOrganization> findByActiveTrue();

    List<DirectoryOrganization> findByOrgType(String orgType);
}
