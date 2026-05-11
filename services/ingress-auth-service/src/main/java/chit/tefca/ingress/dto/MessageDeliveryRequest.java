package chit.tefca.ingress.dto;

import chit.tefca.common.enums.ExchangePurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDeliveryRequest {

    @NotNull
    private ExchangePurpose exchangePurpose;

    @NotBlank
    private String targetOrgId;

    @NotBlank
    private String messageType;

    private String patientId;

    @NotNull
    private Map<String, Object> messageBody;
}
