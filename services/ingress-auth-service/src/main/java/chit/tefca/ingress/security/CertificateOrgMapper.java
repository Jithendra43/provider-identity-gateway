package chit.tefca.ingress.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Resolves an mTLS certificate thumbprint to the owning provider organization
 * id by joining {@code ingress.partner_certificates} → {@code ingress.partners}.
 *
 * <p>This is the source of truth for the {@code providerOrgId} attribute the
 * Prior Authorization flow attaches to every request. Lookups are cached for
 * 5 minutes (Caffeine, in-process) so a stable partner cert thumbprint hits
 * the database at most once per cache TTL even under heavy load.</p>
 *
 * <p>Cache misses log a warning — they indicate either an unrecognised cert
 * (which should never happen because {@code MtlsValidationFilter} already
 * verified trust before this filter runs) or a partner whose
 * {@code ingress.partners} row was deleted out from underneath an active
 * certificate.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CertificateOrgMapper {

    private static final String LOOKUP_SQL = """
            SELECT p.org_id
              FROM ingress.partner_certificates pc
              JOIN ingress.partners             p  ON p.partner_id = pc.partner_id
             WHERE pc.thumbprint = ?
               AND pc.active     = TRUE
               AND pc.not_after  > now()
               AND p.status      = 'ACTIVE'
             LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    @Value("${tefca.pa.org-mapper.cache-ttl-seconds:300}")
    private long cacheTtlSeconds;

    @Value("${tefca.pa.org-mapper.cache-max-size:1024}")
    private long cacheMaxSize;

    private Cache<String, String> cache;

    public Optional<String> resolveOrgId(String thumbprint) {
        if (thumbprint == null || thumbprint.isBlank()) {
            return Optional.empty();
        }
        String cached = cache().getIfPresent(thumbprint);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            String orgId = jdbcTemplate.queryForObject(LOOKUP_SQL, String.class, thumbprint);
            if (orgId != null) {
                cache().put(thumbprint, orgId);
                return Optional.of(orgId);
            }
        } catch (EmptyResultDataAccessException e) {
            log.warn("CertificateOrgMapper — no active partner row for thumbprint={}", thumbprint);
        } catch (Exception e) {
            log.error("CertificateOrgMapper — JDBC lookup failed for thumbprint={}: {}",
                    thumbprint, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Drop a single thumbprint from the cache. Called from the partner
     * onboarding service when a certificate is suspended so the next mTLS
     * request from that cert misses the cache and is rejected by the
     * {@code WHERE active = TRUE} clause in {@link #LOOKUP_SQL}.
     *
     * <p>No-op if the thumbprint isn't currently cached or the cache hasn't
     * been initialised yet.</p>
     */
    public void invalidate(String thumbprint) {
        if (thumbprint == null || thumbprint.isBlank() || cache == null) {
            return;
        }
        cache.invalidate(thumbprint);
    }

    /**
     * Drop every entry from the cache. Useful after a bulk partner change
     * (e.g. operator-initiated suspend-all-on-CA-rotation) where it's
     * cheaper to refill on demand than to enumerate touched thumbprints.
     */
    public void invalidateAll() {
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    private synchronized Cache<String, String> cache() {
        if (cache == null) {
            cache = Caffeine.newBuilder()
                    .maximumSize(cacheMaxSize)
                    .expireAfterWrite(Duration.ofSeconds(cacheTtlSeconds))
                    .build();
        }
        return cache;
    }
}
