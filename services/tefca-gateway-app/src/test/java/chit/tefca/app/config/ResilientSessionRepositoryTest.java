package chit.tefca.app.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RedisSessionConfig.ResilientSessionRepository}.
 *
 * <p>Verifies the core contract:
 * <ul>
 *   <li>{@code IllegalStateException("Session was invalidated")} is silently
 *       swallowed on {@code save}, {@code findById}, and {@code deleteById}.</li>
 *   <li>All other {@code IllegalStateException}s are re-thrown unchanged.</li>
 *   <li>{@code createSession} always delegates unchanged.</li>
 *   <li>Happy-path calls return the delegate result normally.</li>
 * </ul>
 *
 * <p>No Redis infrastructure is required: the tests inject a Mockito mock of
 * {@link SessionRepository}{@code <Session>} directly via the package-private
 * constructor.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ResilientSessionRepository")
class ResilientSessionRepositoryTest {

    @Mock
    private SessionRepository<Session> mockDelegate;

    @Mock
    private Session mockSession;

    private RedisSessionConfig.ResilientSessionRepository<Session> resilient;

    @BeforeEach
    void setUp() {
        resilient = new RedisSessionConfig.ResilientSessionRepository<>(mockDelegate);
    }

    // -------------------------------------------------------------------------
    // createSession — must always delegate without modification
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("createSession()")
    class CreateSession {

        @Test
        @DisplayName("returns the session provided by the delegate")
        void delegatesCreate() {
            when(mockDelegate.createSession()).thenReturn(mockSession);

            Session result = resilient.createSession();

            assertThat(result).isSameAs(mockSession);
            verify(mockDelegate).createSession();
        }
    }

    // -------------------------------------------------------------------------
    // save() — swallow "Session was invalidated", re-throw everything else
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("save(session)")
    class Save {

        @Test
        @DisplayName("delegates successfully when no exception")
        void delegatesNormally() {
            doNothing().when(mockDelegate).save(mockSession);

            assertThatNoException().isThrownBy(() -> resilient.save(mockSession));

            verify(mockDelegate).save(mockSession);
        }

        @Test
        @DisplayName("swallows 'Session was invalidated' without propagating")
        void swallowsSessionInvalidated() {
            doThrow(new IllegalStateException("Session was invalidated"))
                    .when(mockDelegate).save(any());

            // Must NOT throw — the call must complete silently.
            assertThatNoException().isThrownBy(() -> resilient.save(mockSession));
        }

        @Test
        @DisplayName("re-throws IllegalStateException with a different message")
        void rethrowsOtherIllegalStateException() {
            doThrow(new IllegalStateException("Some other state problem"))
                    .when(mockDelegate).save(any());

            assertThatThrownBy(() -> resilient.save(mockSession))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Some other state problem");
        }

        @Test
        @DisplayName("re-throws non-IllegalStateException runtime exceptions")
        void rethrowsOtherRuntimeException() {
            doThrow(new RuntimeException("Redis connection refused"))
                    .when(mockDelegate).save(any());

            assertThatThrownBy(() -> resilient.save(mockSession))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Redis connection refused");
        }
    }

    // -------------------------------------------------------------------------
    // findById() — swallow "Session was invalidated", return null (cache miss)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findById(id)")
    class FindById {

        @Test
        @DisplayName("returns session when delegate succeeds")
        void returnsSessionNormally() {
            when(mockDelegate.findById("sid-1")).thenReturn(mockSession);

            Session result = resilient.findById("sid-1");

            assertThat(result).isSameAs(mockSession);
        }

        @Test
        @DisplayName("returns null when delegate throws 'Session was invalidated'")
        void returnsNullOnSessionInvalidated() {
            when(mockDelegate.findById(anyString()))
                    .thenThrow(new IllegalStateException("Session was invalidated"));

            Session result = resilient.findById("sid-stale");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null when delegate returns null (already expired)")
        void returnsNullWhenDelegateReturnsNull() {
            when(mockDelegate.findById(anyString())).thenReturn(null);

            Session result = resilient.findById("sid-gone");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("re-throws IllegalStateException with a different message")
        void rethrowsOtherIllegalStateException() {
            when(mockDelegate.findById(anyString()))
                    .thenThrow(new IllegalStateException("Unexpected Redis error"));

            assertThatThrownBy(() -> resilient.findById("sid-err"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Unexpected Redis error");
        }
    }

    // -------------------------------------------------------------------------
    // deleteById() — swallow "Session was invalidated", treat as idempotent
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("deleteById(id)")
    class DeleteById {

        @Test
        @DisplayName("delegates successfully when no exception")
        void delegatesNormally() {
            doNothing().when(mockDelegate).deleteById("sid-del");

            assertThatNoException().isThrownBy(() -> resilient.deleteById("sid-del"));

            verify(mockDelegate).deleteById("sid-del");
        }

        @Test
        @DisplayName("swallows 'Session was invalidated' treating delete as idempotent")
        void swallowsSessionInvalidated() {
            doThrow(new IllegalStateException("Session was invalidated"))
                    .when(mockDelegate).deleteById(anyString());

            assertThatNoException().isThrownBy(() -> resilient.deleteById("sid-already-gone"));
        }

        @Test
        @DisplayName("re-throws IllegalStateException with a different message")
        void rethrowsOtherIllegalStateException() {
            doThrow(new IllegalStateException("Lock held"))
                    .when(mockDelegate).deleteById(anyString());

            assertThatThrownBy(() -> resilient.deleteById("sid-locked"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Lock held");
        }
    }

    // -------------------------------------------------------------------------
    // BeanPostProcessor — non-Redis beans must pass through unchanged
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("resilientSessionRepositoryWrapper() BeanPostProcessor")
    class BeanPostProcessorTest {

        @Test
        @DisplayName("non-RedisSessionRepository beans are returned unchanged")
        void doesNotWrapOtherBeans() throws Exception {
            var bpp = RedisSessionConfig.resilientSessionRepositoryWrapper();
            Object otherBean = new Object();
            Object result = bpp.postProcessAfterInitialization(otherBean, "someOtherBean");
            assertThat(result).isSameAs(otherBean);
        }
    }
}
