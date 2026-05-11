package chit.tefca.policy.repository;

import chit.tefca.policy.model.PolicyAuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PolicyAuditRepository extends JpaRepository<PolicyAuditEntry, Long> {

    List<PolicyAuditEntry> findByCorrelationId(String correlationId);

    List<PolicyAuditEntry> findByRequesterOrgIdAndEvaluatedAtBetweenOrderByEvaluatedAtDesc(
            String requesterOrgId, Instant from, Instant to);

    @Query("""
            SELECT a FROM PolicyAuditEntry a
            WHERE (cast(:correlationId as string) IS NULL OR a.correlationId = :correlationId)
              AND (cast(:decision as string) IS NULL OR a.decision = :decision)
              AND (cast(:requesterOrgId as string) IS NULL OR a.requesterOrgId = :requesterOrgId)
              AND (cast(:operation as string) IS NULL OR a.operation = :operation)
              AND (cast(:since as timestamp) IS NULL OR a.evaluatedAt >= :since)
            """)
    Page<PolicyAuditEntry> search(
            @Param("correlationId") String correlationId,
            @Param("decision") String decision,
            @Param("requesterOrgId") String requesterOrgId,
            @Param("operation") String operation,
            @Param("since") Instant since,
            Pageable pageable);
}
