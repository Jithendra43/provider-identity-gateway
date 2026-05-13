package chit.tefca.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;

class InMemorySessionConfigTest {

    private final InMemorySessionConfig config = new InMemorySessionConfig();

    @Test
    void sessionRepositoryAppliesConfiguredTimeout() {
        Duration timeout = Duration.ofMinutes(30);

        MapSessionRepository repo = config.sessionRepository(timeout);

        MapSession created = repo.createSession();
        assertThat(created.getMaxInactiveInterval()).isEqualTo(timeout);
    }

    @Test
    void sessionRepositoryRoundTripsSessions() {
        MapSessionRepository repo = config.sessionRepository(Duration.ofHours(24));

        MapSession session = repo.createSession();
        session.setAttribute("user", "alice");
        repo.save(session);

        MapSession loaded = repo.findById(session.getId());
        assertThat(loaded).isNotNull();
        assertThat((String) loaded.getAttribute("user")).isEqualTo("alice");

        repo.deleteById(session.getId());
        assertThat(repo.findById(session.getId())).isNull();
    }
}
