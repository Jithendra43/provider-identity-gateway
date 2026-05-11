package chit.tefca.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {

    List<AuditEventEntity> findByCorrelationId(String correlationId);

    List<AuditEventEntity> findByRequesterOrgIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            String requesterOrgId, Instant from, Instant to);

    List<AuditEventEntity> findByEventTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
            String eventType, Instant from, Instant to);

    @Query("SELECT a FROM AuditEventEntity a WHERE a.createdAt BETWEEN :from AND :to ORDER BY a.createdAt DESC")
    List<AuditEventEntity> findByTimeRange(Instant from, Instant to);

    @Query("""
            SELECT a FROM AuditEventEntity a
            WHERE (cast(:correlationId as string) IS NULL OR a.correlationId = :correlationId)
              AND (cast(:eventType as string) IS NULL OR a.eventType = :eventType)
              AND (cast(:outcome as string) IS NULL OR a.outcome = :outcome)
              AND (cast(:requesterOrgId as string) IS NULL OR a.requesterOrgId = :requesterOrgId)
              AND (cast(:since as timestamp) IS NULL OR a.createdAt >= :since)
            """)
    Page<AuditEventEntity> search(
            @Param("correlationId") String correlationId,
            @Param("eventType") String eventType,
            @Param("outcome") String outcome,
            @Param("requesterOrgId") String requesterOrgId,
            @Param("since") Instant since,
            Pageable pageable);
}
