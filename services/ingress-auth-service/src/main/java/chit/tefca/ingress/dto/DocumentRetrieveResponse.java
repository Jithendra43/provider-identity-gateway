package chit.tefca.ingress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRetrieveResponse {
    private String documentId;
    private String mimeType;
    private String content;  // Base64-encoded document content
    private String sourceOrgId;
}
