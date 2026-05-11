package chit.tefca.directory.repository;

import chit.tefca.common.enums.Modality;
import chit.tefca.directory.model.DirectoryEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EndpointRepository extends JpaRepository<DirectoryEndpoint, String> {

    List<DirectoryEndpoint> findByNodeIdAndActiveTrue(String nodeId);

    List<DirectoryEndpoint> findByNodeIdAndModalityAndActiveTrue(String nodeId, Modality modality);

    @Query("SELECT e FROM DirectoryEndpoint_DirectoryCache e JOIN DirectoryNode n ON e.nodeId = n.nodeId " +
           "WHERE n.orgId = :orgId AND e.active = true")
    List<DirectoryEndpoint> findActiveByOrgId(@Param("orgId") String orgId);

    @Query("SELECT e FROM DirectoryEndpoint_DirectoryCache e JOIN DirectoryNode n ON e.nodeId = n.nodeId " +
           "WHERE n.orgId = :orgId AND e.modality = :modality AND e.active = true")
    List<DirectoryEndpoint> findActiveByOrgIdAndModality(
            @Param("orgId") String orgId, @Param("modality") Modality modality);

    long countByNodeId(String nodeId);
}
