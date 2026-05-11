package chit.tefca.app.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.MapSession;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.data.redis.RedisSessionMapper;

/**
 * Production hardening for Spring Session over Redis (ElastiCache).
 *
 * <p>Background: Spring Session 3.x's default {@link RedisSessionMapper} throws
 * {@code IllegalStateException("creationTime key must not be null")} whenever a
 * Redis hash for a session is missing the {@code creationTime} field. That can
 * happen for entirely benign reasons in production:
 * <ul>
 *   <li>The hash key TTL fired between the Redis {@code EXISTS} check and the
 *       {@code HGETALL} read (a small but non-zero race window).</li>
 *   <li>The pod was redeployed and the previous boot left a stale, partially
 *       written session.</li>
 *   <li>An admin manually flushed Redis but the browser still presents the
 *       old {@code SESSION} cookie.</li>
 * </ul>
 *
 * <p>Without this customizer, every request carrying a stale cookie returns
 * HTTP 500 (because the exception bubbles all the way through
 * {@code SessionRepositoryFilter}). The user-visible symptom is "every page
 * after login is broken until I clear cookies".
 *
 * <p>Fix: install a mapper that catches the exception and returns {@code null},
 * which is the documented contract for "session not found". Spring Session then
 * creates a fresh session on the next save. The user is silently re-authed via
 * their existing OAuth2 cookie / Cognito SSO.
 *
 * <p>Active only when {@code spring.session.store-type=redis} (the prod default
 * when {@code REDIS_HOST} is wired). Local/dev profiles using in-memory
 * sessions are unaffected.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RedisSessionRepository.class)
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
public class RedisSessionConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionConfig.class);

    @Bean
    public SessionRepositoryCustomizer<RedisSessionRepository> resilientRedisSessionMapper() {
        log.info("Installing resilient RedisSessionMapper (stale/partial sessions will be dropped silently)");
        return repository -> repository.setRedisSessionMapper(new ResilientRedisSessionMapper());
    }

    /**
     * Decorator around the default {@link RedisSessionMapper} that converts
     * "missing required field" failures into a benign cache miss.
     */
    static final class ResilientRedisSessionMapper extends RedisSessionMapper {

        private static final Logger MAPPER_LOG = LoggerFactory.getLogger(ResilientRedisSessionMapper.class);

        @Override
        public MapSession apply(String sessionId, Map<String, Object> map) {
            try {
                return super.apply(sessionId, map);
            } catch (IllegalStateException ex) {
                // Stale/partial session in Redis. Treat as "not found" so
                // Spring Session creates a new one. Logged at WARN once per
                // bad cookie — noisy enough to investigate, quiet enough to
                // not flood CloudWatch under attack.
                MAPPER_LOG.warn("Discarding malformed Redis session {} ({}): {}",
                        sessionId, map == null ? "null" : map.keySet(), ex.getMessage());
                return null;
            }
        }
    }
}
