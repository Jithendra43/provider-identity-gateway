package chit.tefca.policy.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * In-process Caffeine cache for policy rules. The single-task fat-jar
 * deployment does not need a distributed cache; a per-instance Caffeine
 * cache is faster and removes the Redis dependency entirely.
 */
@Configuration
@EnableCaching
public class PolicyCacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager("policyRules");
        mgr.setCaffeine(Caffeine.newBuilder()
                // Hard cap so a stale rule cannot live forever.
                .expireAfterWrite(5, TimeUnit.MINUTES)
                // NOTE: refreshAfterWrite requires a LoadingCache (Caffeine.build(loader)).
                // CaffeineCacheManager builds non-loading caches, so refreshAfterWrite
                // is incompatible here and would crash startup. Lazy-reload on expiry
                // is acceptable for the policy hot path (DB lookup is sub-millisecond).
                .maximumSize(10_000));
        return mgr;
    }
}
