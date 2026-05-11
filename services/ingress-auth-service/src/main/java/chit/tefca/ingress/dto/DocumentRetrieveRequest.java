package chit.tefca.ingress.dto;

import chit.tefca.common.enums.ExchangePurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRetrieveRequest {

    @NotNull
    private ExchangePurpose exchangePurpose;

    @NotBlank
    private String documentId;

    @NotBlank
    private String repositoryId;

    @NotBlank
    private String patientId;

    private String targetOrgId;
}
