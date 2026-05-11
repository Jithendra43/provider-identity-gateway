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
public class DocumentQueryRequest {

    @NotNull
    private ExchangePurpose exchangePurpose;

    @NotBlank
    private String patientId;

    @NotBlank
    private String patientIdSystem;

    private String targetOrgId;

    private String documentType;

    private String dateFrom;

    private String dateTo;
}
