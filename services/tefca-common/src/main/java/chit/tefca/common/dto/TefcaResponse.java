package chit.tefca.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Canonical TEFCA response envelope.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TefcaResponse {

    private String correlationId;

    private String status;

    private Object data;

    private List<String> obligations;

    private Map<String, String> metadata;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private List<ErrorDetail> errors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private String code;
        private String message;
        private String field;
    }
}
