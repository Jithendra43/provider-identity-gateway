package chit.tefca.ingress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentQueryResponse {

    private List<DocumentReference> documents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentReference {
        private String documentId;
        private String documentType;
        private String title;
        private String createdDate;
        private String sourceOrgId;
        private String repositoryId;
    }
}
