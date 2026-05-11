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
public class PatientDiscoveryRequest {

    @NotNull
    private ExchangePurpose exchangePurpose;

    @NotBlank
    private String patientFirstName;

    private String patientLastName;

    private String patientDateOfBirth;

    private String patientGender;

    private String patientIdSystem;

    private String patientId;

    private String targetOrgId;
}
