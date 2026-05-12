package chit.tefca.ingress.exception;

import chit.tefca.common.dto.TefcaResponse;
import chit.tefca.common.exception.DirectoryLookupException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class IngressExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<TefcaResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<TefcaResponse.ErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> TefcaResponse.ErrorDetail.builder()
                        .code("VALIDATION_ERROR")
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();

        return ResponseEntity.badRequest().body(TefcaResponse.builder()
                .status("ERROR")
                .errors(errors)
                .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<TefcaResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("Malformed request body: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is missing or malformed");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<TefcaResponse> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "HTTP method " + ex.getMethod() + " not supported on this endpoint");
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<TefcaResponse> handleNotFound(Exception ex) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    @ExceptionHandler(DirectoryLookupException.class)
    public ResponseEntity<TefcaResponse> handleDirectoryLookup(DirectoryLookupException ex) {
        // All current callers throw this only via Optional.orElseThrow on a
        // missing record, so the external API surface should reflect 404.
        log.debug("Directory lookup miss: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<TefcaResponse> handleWebClientError(WebClientResponseException ex) {
        log.error("Downstream service error: {} {}", ex.getStatusCode(), ex.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "DOWNSTREAM_ERROR",
                "Downstream service returned an error");
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<TefcaResponse> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Insufficient privileges for this operation");
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<TefcaResponse> handleAuthentication(
            org.springframework.security.core.AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication required");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalState(IllegalStateException ex) {
        // A race between logout and response-commit causes SessionRepositoryFilter
        // to call RedisSessionRepository.save() on a session that has already been
        // invalidated. The ResilientSessionRepository wrapper (Phase 1) suppresses
        // this on the save path, but if the exception escapes any other path it
        // must be handled here as a no-op (the session is gone — 204 is correct).
        if ("Session was invalidated".equals(ex.getMessage())) {
            log.debug("Session already invalidated during response commit — returning 204 (no-op)");
            return ResponseEntity.noContent().build();
        }
        log.error("Unhandled IllegalStateException in ingress", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An internal error occurred");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<TefcaResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception in ingress", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An internal error occurred");
    }

    private ResponseEntity<TefcaResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(TefcaResponse.builder()
                .status("ERROR")
                .errors(List.of(TefcaResponse.ErrorDetail.builder()
                        .code(code)
                        .message(message)
                        .build()))
                .build());
    }
}
