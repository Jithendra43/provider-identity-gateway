package chit.tefca.routing.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyManagerTest {

    private IdempotencyManager manager;

    @BeforeEach
    void setUp() {
        manager = new IdempotencyManager();
    }

    @Test
    void shouldDetectDuplicateAfterMark() {
        manager.markProcessed("key-1", "corr-1");
        assertThat(manager.isDuplicate("key-1")).isTrue();
    }

    @Test
    void shouldReturnFalseForNewKey() {
        assertThat(manager.isDuplicate("key-2")).isFalse();
    }

    @Test
    void shouldReturnFalseForNullKey() {
        assertThat(manager.isDuplicate(null)).isFalse();
    }

    @Test
    void shouldReturnFalseForBlankKey() {
        assertThat(manager.isDuplicate("  ")).isFalse();
    }

    @Test
    void shouldNotMarkNullKey() {
        manager.markProcessed(null, "corr-x");
        assertThat(manager.isDuplicate(null)).isFalse();
    }

    @Test
    void shouldNotMarkBlankKey() {
        manager.markProcessed("", "corr-x");
        assertThat(manager.isDuplicate("")).isFalse();
    }
}
