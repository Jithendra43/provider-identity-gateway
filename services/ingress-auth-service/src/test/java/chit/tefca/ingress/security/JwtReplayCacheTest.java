package chit.tefca.ingress.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtReplayCacheTest {

    @Test
    void disabled_alwaysAllows() {
        JwtReplayCache cache = new JwtReplayCache(false);
        assertThat(cache.recordOrDetectReplay("jti-1", "corr-1", Instant.now().plusSeconds(60))).isTrue();
        // even repeated calls allowed when disabled
        assertThat(cache.recordOrDetectReplay("jti-1", "corr-1", Instant.now().plusSeconds(60))).isTrue();
    }

    @Test
    void firstSeen_isAllowed() {
        JwtReplayCache cache = new JwtReplayCache(true);
        boolean allowed = cache.recordOrDetectReplay("jti-1", "corr-1", Instant.now().plusSeconds(120));
        assertThat(allowed).isTrue();
    }

    @Test
    void secondSeen_isRejectedAsReplay() {
        JwtReplayCache cache = new JwtReplayCache(true);
        Instant exp = Instant.now().plusSeconds(120);
        assertThat(cache.recordOrDetectReplay("jti-2", "corr-2", exp)).isTrue();
        assertThat(cache.recordOrDetectReplay("jti-2", "corr-2", exp)).isFalse();
    }

    @Test
    void missingJti_skipsCheckAndAllows() {
        JwtReplayCache cache = new JwtReplayCache(true);
        assertThat(cache.recordOrDetectReplay(null, "corr-1", Instant.now().plusSeconds(60))).isTrue();
        assertThat(cache.recordOrDetectReplay("", "corr-1", Instant.now().plusSeconds(60))).isTrue();
    }

    @Test
    void missingCorrelationId_skipsCheckAndAllows() {
        JwtReplayCache cache = new JwtReplayCache(true);
        assertThat(cache.recordOrDetectReplay("jti-3", null, Instant.now().plusSeconds(60))).isTrue();
        assertThat(cache.recordOrDetectReplay("jti-3", "", Instant.now().plusSeconds(60))).isTrue();
    }

    @Test
    void allowsReplayAfterTokenExpiry() {
        JwtReplayCache cache = new JwtReplayCache(true);
        // Already expired token should not block re-use (cache treats it as fresh)
        Instant past = Instant.now().minusSeconds(1);
        assertThat(cache.recordOrDetectReplay("jti-x", "corr-x", past)).isTrue();
        // second call: previous entry is expired, so should still be allowed
        assertThat(cache.recordOrDetectReplay("jti-x", "corr-x", Instant.now().plusSeconds(60))).isTrue();
    }
}
