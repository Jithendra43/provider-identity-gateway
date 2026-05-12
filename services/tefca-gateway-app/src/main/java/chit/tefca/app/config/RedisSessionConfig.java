package chit.tefca.app.config;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.MapSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.data.redis.RedisSessionMapper;

/**
 * Production hardening for Spring Session over Redis (ElastiCache).
 *
 * <h3>Problem 1 — Stale/partial session on READ</h3>
 * <p>Spring Session 3.x's default {@link RedisSessionMapper} throws
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
 * <p>Fix: {@link ResilientRedisSessionMapper} catches the mapper exception,
 * returns {@code null} (documented contract for "session not found"), and
 * immediately deletes the partial hash key from Redis via
 * {@link StringRedisTemplate}. Without the deletion the same malformed hash
 * causes every subsequent request from that browser to receive 401 forever
 * because (a) the partial hash has no TTL (the delta {@code HSET} that
 * created it does not set {@code EXPIRE}), and (b)
 * {@code SessionRepositoryFilter} only expires the {@code SESSION} cookie
 * in the response when the key is completely absent from Redis.
 *
 * <h3>Problem 2 — {@code Session was invalidated} on SAVE (prod 500 storm)</h3>
 * <p>{@code RedisSessionRepository.save(session)} throws
 * {@code IllegalStateException("Session was invalidated")} when the session has
 * been invalidated (via {@code HttpSession.invalidate()} or Spring Security's
 * logout handler) but the response-commit hook in
 * {@link org.springframework.session.web.http.SessionRepositoryFilter} still
 * calls {@code save()} as part of {@code commitSession()}. This propagates through
 * Tomcat's async dispatch path, is caught by
 * {@code IngressExceptionHandler.handleGeneral()}, which writes a 500 JSON body,
 * whose own flush triggers a second {@code commitSession()} call — creating a
 * cascade that makes every admin UI endpoint return HTTP 500.
 * <p>The same exception can fire from {@code findById} and {@code deleteById}
 * in edge cases (e.g. concurrent logout on the same session from two tabs).
 * <p>Fix: {@link ResilientSessionRepository} wraps the auto-configured
 * {@link RedisSessionRepository} via a static {@link BeanPostProcessor} and
 * swallows any {@code IllegalStateException("Session was invalidated")} on
 * {@code save}, {@code findById}, and {@code deleteById}, treating each as a
 * benign no-op / cache miss (logged at WARN once per occurrence so CloudWatch
 * retains observability without flooding).
 *
 * <p>Both fixes are active only when {@code spring.session.store-type=redis}.
 * Local/dev profiles using in-memory sessions are unaffected.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RedisSessionRepository.class)
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
public class RedisSessionConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionConfig.class);

    /**
     * Installs the resilient read-side mapper so stale/partial Redis hashes
     * (missing {@code creationTime}) are silently dropped AND deleted from Redis.
     *
     * <p>The {@link StringRedisTemplate} is used for the direct key deletion to
     * avoid the infinite-recursion that would occur if we used
     * {@code repository.deleteById()}: Spring Session's {@code deleteById}
     * internally calls {@code findById}, which re-invokes the mapper, which
     * would re-trigger the delete callback.
     */
    @Bean
    public SessionRepositoryCustomizer<RedisSessionRepository> resilientRedisSessionMapper(
            StringRedisTemplate stringRedisTemplate,
            @Value("${spring.session.redis.namespace:spring:session}") String namespace) {
        String sessionKeyPrefix = namespace + ":sessions:";
        log.info("Installing resilient RedisSessionMapper "
                + "(stale/partial sessions will be dropped and deleted from Redis)");
        return repository -> repository.setRedisSessionMapper(
            new ResilientRedisSessionMapper(id -> {
                try {
                    Boolean deleted = stringRedisTemplate.delete(sessionKeyPrefix + id);
                    if (Boolean.TRUE.equals(deleted)) {
                        log.info("Deleted malformed partial-hash session '{}' from Redis "
                                + "(prevents perpetual 401s from stale SESSION cookie)", id);
                    }
                } catch (Exception ex) {
                    log.warn("Failed to delete malformed session '{}' from Redis (best-effort): {}",
                            id, ex.getMessage());
                }
            })
        );
    }

    /**
     * Wraps the auto-configured {@link RedisSessionRepository} bean with
     * {@link ResilientSessionRepository} after all
     * {@link SessionRepositoryCustomizer}s have been applied.
     *
     * <p>A {@code static} {@link BeanPostProcessor} is used so that:
     * <ol>
     *   <li>No circular dependency is introduced — the post-processor runs
     *       without needing the application context to be fully wired.</li>
     *   <li>The {@link SessionRepositoryCustomizer} beans (including
     *       {@link #resilientRedisSessionMapper()}) are applied first, then
     *       the wrapper is applied on top.</li>
     *   <li>{@link org.springframework.session.web.http.SessionRepositoryFilter}
     *       receives the wrapper transparently, because the wrapper implements
     *       the same {@link SessionRepository} interface.</li>
     * </ol>
     */
    @Bean
    public static BeanPostProcessor resilientSessionRepositoryWrapper() {
        return new BeanPostProcessor() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof RedisSessionRepository repository) {
                    log.info("Wrapping RedisSessionRepository '{}' with ResilientSessionRepository "
                            + "(save/findById/deleteById failure guard for 'Session was invalidated')", beanName);
                    // Two-step cast: RedisSessionRepository → SessionRepository<?> → SessionRepository<S>
                    // Required because generics are invariant; the raw-type cast is safe here because
                    // the wrapper only passes calls through to the delegate unchanged.
                    return new ResilientSessionRepository((SessionRepository) repository);
                }
                return bean;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    /**
     * Delegating {@link SessionRepository} that suppresses
     * {@code IllegalStateException("Session was invalidated")} on write/read
     * operations so they degrade gracefully to a no-op or a cache miss
     * instead of propagating a 500 through the response pipeline.
     *
     * <p>Only the specific message {@code "Session was invalidated"} is
     * swallowed. All other {@code IllegalStateException}s (e.g. invalid
     * session data format) are re-thrown unchanged so real bugs are still
     * surfaced in CloudWatch.
     *
     * @param <S> Spring Session {@link Session} type (typically
     *            {@code RedisSessionRepository.RedisSession})
     */
    static final class ResilientSessionRepository<S extends Session> implements SessionRepository<S> {

        private static final String INVALIDATED_MSG = "Session was invalidated";
        private static final Logger REPO_LOG = LoggerFactory.getLogger(ResilientSessionRepository.class);

        private final SessionRepository<S> delegate;

        /**
         * Accepts any {@link SessionRepository}{@code <S>} so the wrapper can
         * be tested without a live Redis connection. The
         * {@link BeanPostProcessor} supplies the auto-configured
         * {@link RedisSessionRepository} cast to this type.
         */
        ResilientSessionRepository(SessionRepository<S> delegate) {
            this.delegate = delegate;
        }

        @Override
        public S createSession() {
            return delegate.createSession();
        }

        /**
         * Delegates to the underlying repository. If the session has already
         * been invalidated (race between logout and response-commit) the
         * exception is swallowed and the save is treated as a no-op. The
         * session data is already gone from Redis (deleted by the logout path)
         * so skipping the save is semantically correct.
         */
        @Override
        public void save(S session) {
            try {
                delegate.save(session);
            } catch (IllegalStateException ex) {
                if (INVALIDATED_MSG.equals(ex.getMessage())) {
                    REPO_LOG.warn("Suppressing save() on already-invalidated session — treating as no-op "
                            + "(session was deleted by logout before response-commit fired)");
                } else {
                    throw ex;
                }
            } catch (DataAccessException ex) {
                REPO_LOG.warn("Suppressing save() Redis error (transient key-not-found / RENAME race) — treating as no-op: {}", ex.getMessage());
            }
        }

        /**
         * Returns {@code null} (session-not-found contract) if the session has
         * been concurrently invalidated, instead of propagating the exception.
         */
        @Override
        public S findById(String id) {
            try {
                return delegate.findById(id);
            } catch (IllegalStateException ex) {
                if (INVALIDATED_MSG.equals(ex.getMessage())) {
                    REPO_LOG.warn("Suppressing findById() for invalidated session '{}' — returning null (session not found)", id);
                    return null;
                }
                throw ex;
            } catch (DataAccessException ex) {
                REPO_LOG.warn("Suppressing findById() Redis error for session '{}' — returning null (transient): {}", id, ex.getMessage());
                return null;
            }
        }

        /**
         * Treats a delete of an already-invalidated session as idempotent.
         */
        @Override
        public void deleteById(String id) {
            try {
                delegate.deleteById(id);
            } catch (IllegalStateException ex) {
                if (INVALIDATED_MSG.equals(ex.getMessage())) {
                    REPO_LOG.warn("Suppressing deleteById() for already-invalidated session '{}' — treating as already deleted", id);
                } else {
                    throw ex;
                }
            } catch (DataAccessException ex) {
                REPO_LOG.warn("Suppressing deleteById() Redis error for session '{}' — treating as already deleted (transient): {}", id, ex.getMessage());
            }
        }
    }

    /**
     * Decorator around the default {@link RedisSessionMapper} that converts
     * "missing required field" failures into a benign cache miss and
     * immediately removes the partial hash from Redis.
     *
     * <h4>Why the deletion is critical</h4>
     * <p>A partial hash (e.g. only {@code lastAccessedTime} present, no
     * {@code creationTime}) is created when a concurrent response-commit
     * {@code save()} delta-writes {@code lastAccessedTime} to a session key
     * that was already deleted by logout. The {@code HSET} command
     * re-creates the key with only the dirty fields and <em>no TTL</em>,
     * so it persists indefinitely. Without deletion every subsequent request
     * from the same browser cookie finds the same malformed hash, receives
     * {@code null} from this mapper, is treated as anonymous, and gets 401
     * forever — even after the user tries to re-authenticate.
     */
    static final class ResilientRedisSessionMapper implements BiFunction<String, Map<String, Object>, MapSession> {

        private static final Logger MAPPER_LOG = LoggerFactory.getLogger(ResilientRedisSessionMapper.class);
        private final RedisSessionMapper delegate = new RedisSessionMapper();
        private final Consumer<String> onMalformed;

        /** No-arg constructor with a no-op callback. Used in tests. */
        ResilientRedisSessionMapper() {
            this(id -> {});
        }

        /**
         * @param onMalformed called with the session ID whenever a malformed
         *                    hash is discarded. Typically deletes the Redis key
         *                    so that {@code SessionRepositoryFilter} can expire
         *                    the stale {@code SESSION} cookie on the response.
         */
        ResilientRedisSessionMapper(Consumer<String> onMalformed) {
            this.onMalformed = onMalformed;
        }

        @Override
        public MapSession apply(String sessionId, Map<String, Object> map) {
            try {
                return delegate.apply(sessionId, map);
            } catch (IllegalStateException ex) {
                // Stale/partial session in Redis — treat as "not found" and
                // trigger the cleanup callback (usually a Redis DEL). Logged
                // at WARN: noisy enough to investigate, quiet enough to not
                // flood CloudWatch under attack.
                MAPPER_LOG.warn(
                        "Discarding malformed Redis session {} ({}): {}. "
                        + "Scheduling key deletion to prevent perpetual 401s.",
                        sessionId, map == null ? "null" : map.keySet(), ex.getMessage());
                onMalformed.accept(sessionId);
                return null;
            }
        }
    }
}
