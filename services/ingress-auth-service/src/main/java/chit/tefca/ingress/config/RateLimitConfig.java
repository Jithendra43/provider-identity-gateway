package chit.tefca.ingress.config;

import chit.tefca.ingress.model.PartnerRateLimit;
import chit.tefca.ingress.repository.PartnerRateLimitRepository;
import chit.tefca.ingress.repository.PartnerRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimitConfig {

    @Value("${tefca.rate-limit.requests-per-minute:100}")
    private int defaultRequestsPerMinute;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final PartnerRepository partnerRepository;
    private final PartnerRateLimitRepository rateLimitRepository;

    public RateLimitConfig(
            @org.springframework.lang.Nullable PartnerRepository partnerRepository,
            @org.springframework.lang.Nullable PartnerRateLimitRepository rateLimitRepository) {
        this.partnerRepository = partnerRepository;
        this.rateLimitRepository = rateLimitRepository;
    }

    @Bean
    public Map<String, Bucket> rateLimitBuckets() {
        return buckets;
    }

    /**
     * Resolves a per-org rate limit bucket. Checks partner_rate_limits table first;
     * falls back to the global default if no DB record exists.
     */
    public Bucket resolveBucket(String orgId) {
        return buckets.computeIfAbsent(orgId, key -> {
            int rpm = resolveRpm(key);
            return Bucket.builder()
                    .addLimit(Bandwidth.simple(rpm, Duration.ofMinutes(1)))
                    .build();
        });
    }

    private int resolveRpm(String orgId) {
        if (partnerRepository == null || rateLimitRepository == null) {
            return defaultRequestsPerMinute;
        }
        return partnerRepository.findByOrgId(orgId)
                .flatMap(partner -> rateLimitRepository.findByPartnerIdAndActiveTrue(partner.getPartnerId()))
                .map(PartnerRateLimit::getRequestsPerMinute)
                .orElse(defaultRequestsPerMinute);
    }
}
