package chit.tefca.directory.repository;

import chit.tefca.common.enums.NodeStatus;
import chit.tefca.directory.model.DirectoryNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NodeRepository extends JpaRepository<DirectoryNode, String> {

    List<DirectoryNode> findByOrgId(String orgId);

    List<DirectoryNode> findByOrgIdAndStatus(String orgId, NodeStatus status);

    List<DirectoryNode> findByStatus(NodeStatus status);

    @Query("SELECT n FROM DirectoryNode n WHERE n.orgId = :orgId AND n.status = 'ACTIVE'")
    List<DirectoryNode> findActiveByOrgId(@Param("orgId") String orgId);

    long countByOrgId(String orgId);
}
