package chit.tefca.directory.repository;

import chit.tefca.common.enums.Modality;
import chit.tefca.common.enums.TefcaOperation;
import chit.tefca.directory.model.DirectoryCapability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CapabilityRepository extends JpaRepository<DirectoryCapability, String> {

    List<DirectoryCapability> findByNodeIdAndEnabledTrue(String nodeId);

    List<DirectoryCapability> findByNodeIdAndModalityAndEnabledTrue(String nodeId, Modality modality);

    boolean existsByNodeIdAndModalityAndOperationAndEnabledTrue(
            String nodeId, Modality modality, TefcaOperation operation);
}
