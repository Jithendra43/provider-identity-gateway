package chit.tefca.app.config;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;

/**
 * In-memory Spring HTTP session store backed by {@link MapSessionRepository}.
 *
 * <p>Replaces the previous Redis-backed session store. The gateway runs as a
 * single ECS Fargate task, so cross-pod session replication is not required.
 * Sessions reset on application restart; OIDC re-login is transparent to
 * end-users.
 *
 * <p>The Spring Session abstraction is preserved (cookie name {@code SESSION},
 * standard {@code HttpSession} semantics) so SPA, security filters, and
 * logout handlers require no changes.
 */
@Configuration
@EnableSpringHttpSession
public class InMemorySessionConfig {

    private static final Logger log = LoggerFactory.getLogger(InMemorySessionConfig.class);

    @Bean
    public MapSessionRepository sessionRepository(
            @Value("${spring.session.timeout:PT24H}") Duration timeout) {
        log.info("Using in-memory session store (MapSessionRepository) timeout={}", timeout);
        MapSessionRepository repository = new MapSessionRepository(new ConcurrentHashMap<>());
        repository.setDefaultMaxInactiveInterval(timeout);
        return repository;
    }
}
